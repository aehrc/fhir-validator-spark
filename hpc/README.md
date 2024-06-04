
### Env Setup

Modules:

- amazon-corretto/11.0.14.10.1
- maven/3.8.1

```bash
module load amazon-corretto/11.0.14.10.1 maven/3.8.1 
```

Then use conda env (TBP)

- conda with python 3.11
- pip install -r env/requirements.txt

Setup spark configuration defaults (TBP):

```bash
export SPARK_CONF_DIR=$PWD/env/spark-conf
```