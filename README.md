

### TODO

- [ ] Cleanup the code for ValidationEngine management
- [ ] Cleanup the code for ValidationResult (include the basic enum)
- [ ] Add option to filter out only results with issues
- [X] Add and test other options that need to be passed to the validator (version, ig etc)
- [ ] Test locally, on HPC and databricks on subset of mimic-fhir
- [ ] Reorganize project and add to the github repo for 'fhir--mimic'
- [X] Work on reports from the validation results
- [ ] Add version detection for the validator from the IGs and compare the loading process to Validator CLI.



### Ideas

- Add highlighting or errors to the validation results. (e.g. red for errors, yellow for warnings, green for info). 
- Automatic translation - make LMM to produce the prompt based on the examples.
- Develop a generalised map(reduce) python for Slurm Array Jobs.

[//]: # (TODO: Someting)
