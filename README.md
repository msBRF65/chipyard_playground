# chipyard_playground
## how to use this repo
```bash
sh setup.sh
cd chipyard
source env.sh
```

## execute accumulator test
```bash
cd chipyard/sims/vcs
make run-binary-debug -j8 CONFIG=CustomAcceleratorConfig BINARY=../../tests/accum.riscv USE_VPD=1
```

