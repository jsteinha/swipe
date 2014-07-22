import sys

filename = sys.argv[1]
lines = file(filename, "r").readlines()
on = 0
listT = list()
for line in lines:
  if line.find("train correct") != -1:
    on = 1
  if line.find("test correct") != -1:
    on = 0
  if on == 1:
    target = "average T:"
    pos = line.find(target)
    if pos != -1:
      T = float(line[pos+len(target):])
      listT.append(T)
print listT


