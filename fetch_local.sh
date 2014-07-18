#!/bin/bash

mkdir -p $1
NUM=`ls -n state/execs/$1/*.exec/record | wc -l`
for n in `seq 0 $((NUM-1))`
do
  cp state/execs/$1/$n.exec/record $1/record-$n
done
