#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

export JAVA_HOME=/usr/lib/jvm/jdk-11.0.1
#export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
java=$JAVA_HOME/bin/java

cd $script_dir

# build the project
mvn package -DskipTests

exp=$1
res=$2
if [ "$#" -ne 2 ]; then
    echo "Illegal number of parameters. Syntax: run.sh <exp events file> <res events file>"
    exit 1
fi

# run the validator
$java -Xmx12g \
    -cp target/matsim-sim-validator-1.0-SNAPSHOT.jar \
    ch.ethz.systems.hermes.App $exp $res \
        $script_dir | tee $script_dir/run.log

grep "skew" run.log | awk '{ print $2 " " $5}' > skew.dat
./plot-skew.gnuplot

cd - &> /dev/null

paplay /usr/share/sounds/freedesktop/stereo/complete.oga
#beep
