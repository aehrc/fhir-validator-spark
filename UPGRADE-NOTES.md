# Upgrade notes: Spark 4, Java 21 and the session-callable API

Author: John Grimes

Branch `spark-4-and-session-api`, cut from `2469846` ("Finalizing release
1.0.0"), the tip of `main` at the time of the upgrade.

## Why

Pathling 9.x requires `pyspark>=4.0.0,<4.1.0`, which runs on Scala 2.13 and
needs a Java 17 or later runtime. The released `fhir-validator-spark` 1.0.0
targets Spark 3.4.3, Scala 2.12 and Java 11 bytecode, so the two cannot share a
runtime, and the validator cannot be called from inside a Pathling session at
all because the only entry point is `spark-submit`.

## Baseline: `mvn clean install` on the unmodified code, under Java 21

Environment: Zulu 21.0.12.1 (`openjdk version "21.0.12.1" 2026-08-18 LTS`),
Apache Maven 3.9.16, macOS 15 on arm64.

The build compiles, then fails in the test phase. Every test that starts a
`SparkContext` dies while Spark's `Platform` class initialises:

```
[ERROR] Errors:
[ERROR]   ValidateAppTest.testPartitionedDataset:20 » ExceptionInInitializer
[ERROR]   ValidateAppTest.testSingleFile:12 » NoClassDefFound Could not initialize class org.apache.spark.unsafe.array.ByteArrayMethods
[ERROR]   ValidateHapiAppTest.testMain:12 » ExceptionInInitializer
[INFO]
[ERROR] Tests run: 1103, Failures: 0, Errors: 3, Skipped: 8
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:20 min
```

The root cause, from the stack trace:

```
java.lang.ExceptionInInitializerError
	at org.apache.spark.unsafe.array.ByteArrayMethods.<clinit>(ByteArrayMethods.java:52)
	...
	at org.apache.spark.SparkContext.<init>(SparkContext.scala:464)
Caused by: java.lang.IllegalStateException: java.lang.NoSuchMethodException: java.nio.DirectByteBuffer.<init>(long,int)
	at org.apache.spark.unsafe.Platform.<clinit>(Platform.java:113)
Caused by: java.lang.NoSuchMethodException: java.nio.DirectByteBuffer.<init>(long,int)
	at java.base/java.lang.Class.getDeclaredConstructor(Class.java:2932)
	at org.apache.spark.unsafe.Platform.<clinit>(Platform.java:71)
```

`java.nio.DirectByteBuffer(long, int)` was removed in JDK 21, and Spark 3.4.3
reflects on it unconditionally. No JVM flag recovers this; the only fix is a
Spark version that knows about JDK 21, which means Spark 4.

## Baseline test list

| Test class | Tests | Errors | Skipped |
| --- | ---: | ---: | ---: |
| `au.csiro.fhir.validation.cli.ValidateAppTest` | 2 | 2 | 0 |
| `au.csiro.fhir.validation.cli.ValidateHapiAppTest` | 1 | 1 | 0 |
| `au.csiro.fhir.validation.hapi.HapiHL7ValidationServiceTest` | 2 | 0 | 0 |
| `au.csiro.fhir.validation.hl7.HL7MessageResolverTest` | 1087 | 0 | 0 |
| `au.csiro.fhir.validation.hl7.HL7ValidationServiceTest` | 3 | 0 | 0 |
| `au.csiro.fhir.validation.snippets.TestCliTerminology` | 4 | 0 | 4 |
| `au.csiro.fhir.validation.snippets.TxValidationTest` | 4 | 0 | 4 |
| **Total** | **1103** | **3** | **8** |

The eight skipped tests are the `snippets` classes, which are disabled
exploratory scratch pads rather than assertions.

## What the upgrade changes

| Aspect | Released 1.0.0 | This branch |
| --- | --- | --- |
| Spark | 3.4.3 | 4.0.2 |
| Scala | 2.12 | 2.13 |
| Java bytecode target | 11 | 17 |
| `org.hl7.fhir.validation` | 6.3.10 | 6.5.26 |
| `hapi-fhir-structures-r4` / `-r5` | 6.10.0 | 8.2.0 |
| Entry points | `spark-submit` CLI only | CLI, unchanged, plus `au.csiro.fhir.validation.spark.ValidationApi` |

The CLI surface, the output Parquet schema and the Python report scripts are
unchanged, so existing invocations and existing result datasets keep working.

### Library API changes absorbed

- `ValidationEngineBuilder.withTHO(boolean)` is gone. The replacement is
  `withThoVersion(String)`, and a null version, which is the default, skips
  loading the terminology package exactly as `withTHO(false)` did.
- `org.hl7.fhir.validation.cli.utils.ValidationLevel` moved to
  `org.hl7.fhir.validation.service.utils.ValidationLevel`.
- `org.hl7.fhir.validation.ValidatorCli` is no longer published in
  `org.hl7.fhir.validation`, and there is no 6.5.26 release of
  `org.hl7.fhir.validation.cli`. The two disabled scratch pads that drove it,
  `snippets/TestCliTerminology` and `snippets/apps/MainCli`, are deleted;
  nothing in the project's own surface used it.

### Behaviour changes absorbed

- A JSON parse failure no longer carries a location. It used to report
  `(document)`. `HL7ValidationServiceTest.testInvalidJson` records the new
  behaviour.
- HAPI 8.2.0 disables `StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION`, so the
  tail of a Jackson parse error now reads `at [Source: REDACTED ...]` rather
  than `at [Source: UNKNOWN ...]`, and the reported column moves by one.
  `HapiHL7ValidationServiceTest.testJsonParsingError` now asserts the level,
  the type and the HAPI error, rather than Jackson's source location.
- `Type_Specific_Checks_DT_URL_Resolve` was reworded from
  `URL value ''{0}'' does not resolve` to
  `No definition could be found for URL value ''{0}''`. The sample in
  `HL7MessageResolverTest` is updated to the current wording.
- 6.5.26 adds message templates that are word for word identical to others,
  such as `QUESTIONNAIRE_QR_ITEM_{DATE,DECIMAL,INTEGER,TIME}_MAX`, all of which
  are `The value {0} is greater than the allowed maximum of {1}`, and
  `VALUESET_REFERENCE_INVALID_TYPE_NO_VERSION_1` and `_2`. Those ids cannot be
  told apart from a message, which the hand maintained `EXCLUDE` list in
  `HL7MessageResolver` used to patch three at a time. It is replaced by a
  general rule: of each group of identical templates, keep the one with the
  lowest id. That reproduces the previous choice for all three of the excluded
  ids and needs no maintenance when the message bundle changes again.

### Why the Parquet schema cannot have changed

The output schema is whatever `Encoders.bean` makes of
`ValidateApp.ResourceWithIssues`, and that class is untouched. Spark's bean
inference is also untouched between the two releases: the field list, the
ordering by property name, the `javax.annotation.Nonnull` nullability rule and
the array `containsNull` rule in `JavaTypeInference` are identical in
`v3.4.3` and `v4.0.2`. `ValidationApiTest` pins the API's `issues` column to
the same bean derived type, so the two producers cannot drift apart either.

The schema `bin/validate-fhir` writes, read back with `pyarrow`:

```
filename: string not null
issues: list<element: struct<col: int32, level: string not null, line: int32, location: string, message: string not null, messageId: string, type: string not null>>
resource: string not null
```

### Verification

- `mvn clean install` on Java 21: `Tests run: 1380, Failures: 0, Errors: 0,
  Skipped: 4`, `BUILD SUCCESS`. The test count rose from the baseline's 1103
  because `HL7MessageResolverTest` is generated from the validator's message
  bundle, which has grown; the skipped four are the remaining disabled
  scratch pads.
- `bin/validate-fhir data/mimic-iv-demo-10/MimicPatient.ndjson target/out --ig
  data/packages/kindlab.fhir.mimic/package.tgz` writes ten rows, and
  `bin/validation-report-issues.py target/out target/report-patient.html
  --min-level 0` renders them with message ids resolved.
