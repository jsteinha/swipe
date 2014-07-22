#!/bin/bash

mkdir -p $1
NUM=`ls -n $1/record-* | wc -l`
for n in `seq 0 $((NUM-1))`
do
  awk '/test correct/ { print $5; }' $1/record-$n > $1/test-$n
  awk '/test time/ { print $5; }' $1/record-$n > $1/testtime-$n
done
LAST=$NUM
if [ ! -z "$2" ] 
  then
    LAST=$2
fi
echo $LAST
python plot2.py $1 $NUM $LAST
open plot.pdf
open plottime.pdf