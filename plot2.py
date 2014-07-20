#names = ['series', 'series2', 'series3', 'series4']
#colors = ['r', 'g', 'b', 'm']
import sys
import numpy as np
colors = ['r', 
          'b',
          'g',
          'm',
          'k',
         ]
style = ['x-', '.-']
name = sys.argv[1]
num_series = int(sys.argv[2])
num_plots = int(sys.argv[3])
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
plt.hold(True)
for n in range(num_series-num_plots, num_series):
  ni = n-num_series+num_plots
  f = open('%s/test-%d' % (name, n))
  nums = []
  for line in f:
    nums.append(float(line.rstrip(',\n')))
  times = []
  try:
    f = open('%s/testtime-%d' % (name, n))
    for line in f:
      times.append(float(line.rstrip(',\n')))
  except:
    print 'exception'
    pass
  f = open('%s/record-%d' % (name, n))
  metadata = []
  for k, line in enumerate(f):
    if k == 1:
      metadata.append(line.split()[2])
    if k == 2:
      for token in line.split()[1:6]:
        metadata.append(token)
    if k == 3:
      train_size = int(line.split()[1])
      test_frequency = int(line.split()[3])
  print metadata
  if len(nums) > 0:
    plt.figure(1)
    plt.plot(np.array(range(len(nums)))*float(test_frequency)/float(train_size), nums, '%s%s' % (colors[ni % len(colors)], style[ni / len(colors)]), label=' '.join(metadata))
  if len(times) > 0:
    plt.figure(2)
    plt.plot(np.array(range(len(nums)))*float(test_frequency)/float(train_size), times, '%s%s' % (colors[ni % len(colors)], style[ni / len(colors)]), label=' '.join(metadata))
plt.figure(1)
plt.title(name+" (accuracy) ")
plt.xlabel('Effective passes through training data')
plt.ylabel('Test accuracy')
plt.axis()
plt.legend(loc=4,prop={'size':9})
plt.savefig('plot.pdf')

plt.figure(2)
plt.title(name+" (time) ")
plt.xlabel('Effective passes through training data')
plt.ylabel('# Test transitions')
plt.axis()
plt.legend(loc=4,prop={'size':9})
plt.savefig('plottime.pdf')

