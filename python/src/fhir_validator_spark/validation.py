"""Validation of FHIR resources with the validator JAR in a Spark session."""


def maven_version(package_version):
    raise NotImplementedError


def validator_args(fhir_version=None, igs=(), language=None, tx_server=None, extra=()):
    raise NotImplementedError


def spark_conf(package_version, master=None, driver_memory=None, jar=None, conf=()):
    raise NotImplementedError


def run_validation(input_path, output_path, *, spark_conf, validator_args, log_progress, log_level):
    raise NotImplementedError
