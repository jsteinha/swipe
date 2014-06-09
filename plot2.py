#names = ['series', 'series2', 'series3', 'series4']
#colors = ['r', 'g', 'b', 'm']
import sys
colors = ['r', 
          'b',
          'g',
          'm',
          'k',
         ]
style = ['x-', '.-']
name = sys.argv[1]
num_series = int(sys.argv[2])
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
plt.hold(True)
for n in range(num_series):
  f = open('%s/test-%d' % (name, n))
  nums = []
  for line in f:
    nums.append(float(line.rstrip(',\n')))
  f = open('%s/record-%d' % (name, n))
  metadata = []
  for k, line in enumerate(f):
    if k == 1:
      metadata.append(line.split()[2])
    if k == 2:
      for token in line.split()[1:6]:
        metadata.append(token)
  print metadata
  plt.plot(nums, '%s%s' % (colors[n / 2], style[n % 2]), label=' '.join(metadata))
plt.title(name)
plt.legend(loc=4,prop={'size':9})
#plt.show()
plt.savefig('plot.pdf')
