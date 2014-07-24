import os

data = file('train2.dat').readlines()
unique = dict()
output = list()
for line in data:
  word = line.split(' ')[0]
  if len(word) == 2 or len(word) == 8:
    print word
    output.append(line)
    unique[word] = 1
print 'length = ', len(output)
print 'unique = ', len(unique.keys())
output_file = open('train2_unbalanced.dat', 'w')
[output_file.write(line) for line in output]
output_file.close()