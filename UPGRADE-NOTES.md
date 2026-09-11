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
