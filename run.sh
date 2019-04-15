#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

export JAVA_HOME=/usr/lib/jvm/jdk-11.0.2
#export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
java=$JAVA_HOME/bin/java

cd $script_dir

# build the project
mvn package -DskipTests

input=$1
if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters. Syntax: run.sh <log file>"
    exit 1
fi

cat $input | grep "ETHZ qsim event" | cut -d " " -f 4- > qsim.log
cat $input | grep "ETHZ hermes event" | cut -d " " -f 4- > hermes.log

# run the validator
$java -Xmx12g \
    -cp target/matsim-sim-validator-1.0-SNAPSHOT.jar \
    ch.ethz.systems.hermes.App \
        qsim $script_dir/qsim.log \
        hermes $script_dir/hermes.log \
        $script_dir | tee $script_dir/run.log

grep "skew" run.log | awk '{ print $2 " " $5}' > skew.dat
./plot-skew.gnuplot

cd - &> /dev/null

paplay /usr/share/sounds/freedesktop/stereo/complete.oga
beep
