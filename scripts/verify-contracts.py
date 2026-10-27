"""Validate source contracts, examples, actual Java JSON and generated public TypeScript."""
import json
from pathlib import Path
import re

from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import OpenAPIV31SpecValidator
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
CHECKER = FormatChecker()


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def errors_with_context(errors):
    for error in errors:
        yield error
        yield from errors_with_context(error.context)


def check_case(validator, value, case):
    errors = list(validator.iter_errors(value))
    if case["valid"]:
        if errors:
            raise AssertionError(f"{case['file']}: {errors[0].message}")
    else:
        assert errors, f"{case['file']}: invalid example accepted"
        assert any(
            e.validator == case["keyword"]
            and "/" + "/".join(map(str, e.absolute_path)) == (case["path"] or "/")
            for e in errors_with_context(errors)
            if "branch" not in case or list(e.absolute_schema_path)[:2] == ["oneOf", case["branch"]]
        ), f"{case['file']}: failed for a different reason"


def ts_type(schema):
    if "$ref" in schema:
        return schema["$ref"].rsplit("/", 1)[1]
    if "const" in schema:
        return json.dumps(schema["const"], ensure_ascii=False)
    if "enum" in schema:
        return " | ".join(json.dumps(v, ensure_ascii=False) for v in schema["enum"])
    if "oneOf" in schema or "anyOf" in schema:
        return " | ".join(ts_type(s) for s in schema.get("oneOf", schema.get("anyOf")))
    kind = schema["type"]
    if kind in ("string", "boolean", "null"):
        return kind
    if kind in ("integer", "number"):
        return "number"
    if kind == "array":
        return f"Array<{ts_type(schema['items'])}>"
    if kind == "object":
        if "properties" not in schema:
            return f"Record<string, {ts_type(schema['additionalProperties'])}>"
        fields = [
            json.dumps(name) + ("" if name in schema.get("required", []) else "?")
            + ": " + ts_type(value)
            for name, value in schema["properties"].items()
        ]
        return "{ " + "; ".join(fields) + " }"
    raise AssertionError(f"Unsupported type: {kind}")


def main():
    if not __debug__:
        raise RuntimeError("Run contract checks without Python optimization")
    event_dir = ROOT / "contracts/events"
    event_validators = {}
    for path in event_dir.glob("*-v1.schema.json"):
        schema = read(path)
        Draft202012Validator.check_schema(schema)
        event_validators[path.name] = Draft202012Validator(schema, format_checker=CHECKER)
    assert len(event_validators) == 3
    cases = read(event_dir / "examples/manifest.json")
    assert {c["file"] for c in cases} == {
        p.name for p in (event_dir / "examples").glob("*.json") if p.name != "manifest.json"
    }
    for case in cases:
        check_case(event_validators[case["schema"]], read(event_dir / "examples" / case["file"]), case)
        if case["valid"]:
            actual = ROOT / "modules/contracts-java/target/serialized-messages" / case["file"]
            assert actual.exists(), f"Run Maven tests first: {actual}"
            check_case(event_validators[case["schema"]], read(actual), case)

    public_path = ROOT / "contracts/http/public-api.yaml"
    public = read(public_path)
    internal_path = ROOT / "contracts/http/booking-internal.yaml"
    internal = read(internal_path)
    for path, api in [(public_path, public), (internal_path, internal)]:
        OpenAPIV31SpecValidator(api, base_uri=path.as_uri()).validate()
    # Register the local document; no network resolution of public model refs.
    doc = {**public, "$id": public_path.as_uri(), "$schema": "https://json-schema.org/draft/2020-12/schema"}
    registry = Registry().with_resource(public_path.as_uri(), Resource.from_contents(doc))
    def public_validator(name):
        return Draft202012Validator(
            {"$ref": public_path.as_uri() + "#/components/schemas/" + name},
            registry=registry, format_checker=CHECKER,
        )
    schemas = public["components"]["schemas"]
    event_schema = read(event_dir / "events-v1.schema.json")
    for index, name in enumerate(("CommandCompleted", "AvailabilityChanged")):
        assert {k: v for k, v in schemas[name].items() if k != "examples"} == event_schema["oneOf"][index]
    example_count = 0
    for name, schema in schemas.items():
        for example in schema.get("examples", []):
            public_validator(name).validate(example)
            example_count += 1
    for case in read(ROOT / "contracts/http/examples/invalid.json"):
        if "schema" in case:
            validator = public_validator(case["schema"])
        else:
            params = public["paths"][case["route"]][case["method"]]["parameters"]
            schema = next(p["schema"] for p in params if p["name"] == case["parameter"])
            validator = Draft202012Validator(schema, format_checker=CHECKER)
        check_case(validator, case["value"], case)

    generated = "// Generated from contracts/http/public-api.yaml; checked by scripts/verify-contracts.py.\n"
    generated += "\n".join(f"export type {name} = {ts_type(schema)};\n" for name, schema in schemas.items())
    models = ROOT / "frontend/src/app/core/http/api.models.ts"
    assert models.read_text(encoding="utf-8") == generated, "Public TypeScript differs from OpenAPI"
    fixture_source = (models.parent / "api.models.spec.ts").read_text(encoding="utf-8")
    fixtures = re.findall(r"const \w+ = (\{.*?\}) satisfies (\w+);", fixture_source, flags=re.S)
    assert len(fixtures) == example_count - 2, "Missing typed public examples (events validated by Java)"
    for value, name in fixtures:
        public_validator(name).validate(json.loads(value))
        assert json.loads(value) == schemas[name]["examples"][0], f"{name}: frontend fixture drift"

    assert all("X-Actor-Id" not in [p["name"] for p in op.get("parameters", [])]
               for item in public["paths"].values() for op in item.values())
    for item in internal["paths"].values():
        for method, op in item.items():
            assert method == "get" or op["operationId"] in ("internalconsumerpause", "internalconsumerresume")
            assert op["security"] == [{"technicalBasic": []}]
            assert {"X-Actor-Id", "X-Actor-Role"} <= {p["name"] for p in op["parameters"]}
    print(f"Contracts OK: 2 OpenAPI, 3 JSON Schema, {len(cases)} event cases, "
          f"6 actual Java messages, {example_count} HTTP examples, {len(fixtures)} typed frontend examples")


if __name__ == "__main__":
    main()
