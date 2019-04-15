#!/usr/bin/gnuplot

set terminal pngcairo enhanced color solid font 'Verdana,12' size 1200,300
set output "skew.png"

set xlabel "Percentage of Travel Time Skew"
set ylabel "Number of Agents"

set xrange [-1:100]

set boxwidth 0.5
set style fill solid
plot "skew.dat" using 1:2 with boxes notitle
