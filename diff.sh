#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Illegal number of parameters"
    echo "Syntax:  diff.sh <matsim log file> <agent id>"
    echo "Example: diff.sh run.log 213983123"
fi

log=$1
agent=$2

cat $log | grep "$agent" | grep "qsim" > qsim.log
cat $log | grep "$agent" | grep "hermes" > hermes.log

vimdiff qsim.log hermes.log


