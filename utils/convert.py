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

def GetGlucose(bytes):
    bitmask = 0x0FFF;
    return "Glucose: "+str(((bytes & bitmask) / 6) - 37)

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

nextWriteBlock1 = int(allcontent[26*2:26*2+2],16)
nextWriteBlock2 = int(allcontent[27*2:27*2+2],16)

# a partir de la posicion 0x1C empieza la lista de los valores 56 = 0x1C * 2
# write block 1
lineNumber = 0
for i in xrange(56,16*6*2+56,12):
    record = allcontent[i:i+12]
    bytes = [record[i:i+2] for i in range(0, len(record), 2)]


    glucose = int("0x"+bytes[1]+bytes[0], 16)

    line = " ".join(bytes)

    if (nextWriteBlock1 - lineNumber != 0):
        line = line + "    | " + str(nextWriteBlock1 - lineNumber) + " min | " + GetGlucose(glucose)

    if (lineNumber == nextWriteBlock1):
        line = line + "    | Now | " + GetGlucose(glucose)
        nextWriteBlock1 += 16

    print line
    f.write(line+'\n')
    lineNumber += 1

f.write("------------------\n")
print "------------------"

# write block 2
lineNumber = 0
for i in xrange(16*6*2+56,48*6*2+56,12):
    record = allcontent[i:i+12]
    bytes = [record[i:i+2] for i in range(0, len(record), 2)]
    glucose = int("0x"+bytes[1]+bytes[0], 16)
    line = " ".join(bytes)

    if (nextWriteBlock2 - lineNumber != 0):
        line = line + "    | " + str((nextWriteBlock2 - lineNumber)*15) + " min | " + GetGlucose(glucose)

    if (lineNumber == nextWriteBlock2):
        line = line + "    | Last | " + GetGlucose(glucose)
        nextWriteBlock2 += 32

    print line
    f.write(line+'\n')
    lineNumber += 1

f.close()
