# Network simulator

`NetworkSim` is an application running set of processes of a distributed computer.
To see more information and the list of available arguments run it with `--help` argument.

<details>
<summary>Run with sbt</summary>

```sh
sbt "project sim" "run --help"
```

</details>

<details>
<summary>Assemble and run uberjar</summary>

```sh
sbt "project sim" assembly
# observe sim.jar in the `target` folder
java -jar sim.jar --help
```

</details>