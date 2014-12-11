#!/usr/bin/env python
import sys
from datetime import datetime, timedelta

def GetTime(minutes):
    t3 = minutes
    t4 = t3/1440
    t5 = t3-(t4*1440)
    t6 = (t5/60);
    t7 = t5-(t6*60)
    print("Sensor active for %d days %d hours and %d minutes" % (t4, t6, t7))

if len(sys.argv) == 1:
    print "Use: convert.py /paht/to/log/log_name.log"
    exit()

log = sys.argv[1]


with open(log) as f:
    content = [line.strip() for line in f]
allcontent = ''
for s in content:
    allcontent += s[:16]

print allcontent[14*2:14*2+2]
print allcontent[15*2:15*2+2]

# Siguiente registro a escribir en la lista de minutos posicion 0x1A
print ("Next reading write position: %s" % allcontent[26*2:26*2+2])

# Siguiente registro a escribir en la media de las ultimas 8 horas posicion 0x1B
print ("Next reading write position: %s" % allcontent[27*2:27*2+2])


iniciado = allcontent[317*2:317*2+2] + allcontent[316*2:316*2+2]
print ("Sensor started: %s" % iniciado)

GetTime(int(iniciado, 16))

f = open('output.txt','wb')

# a partir de la posicion 0x1C empieza la lista de los valores 56 = 0x1C * 2
for i in xrange(56,48*6*2+56,12):
    print allcontent[i:i+12]
    f.write(allcontent[i:i+12]+'\n')
    #print int(allcontent[i+8:i+10],16)
    #print int(allcontent[i+6:i+8],16)
f.close() 

