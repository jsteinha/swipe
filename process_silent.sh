#!/bin/bash

mkdir -p $1
NUM=`ls -n $1/record-* | wc -l`
for n in `seq 0 $((NUM-1))`
do
  awk '/test correct/ { print $5; }' $1/record-$n > $1/test-$n
done
python plot2.py $1 $NUM
# open plot.pdf
