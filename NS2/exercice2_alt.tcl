#create a simulator
set ns [new Simulator]

#trace files: nam and tr
set tf [open out.tr w]
$ns trace-all $tf
set nf [open out.nam w]
$ns namtrace-all $nf

$ns color 0 Blue
$ns color 1 Red

#finish procedure
proc finish {} {
	global ns nf tf
	$ns flush-trace
	close $tf
	close $nf
	exec nam out.nam &
	exit 0
}

#defining the topology
set n0 [$ns node]
set n1 [$ns node]
set n2 [$ns node]
set n3 [$ns node]
set n4 [$ns node]
set n5 [$ns node]
$ns duplex-link $n0 $n1 10Mb 10ms DropTail
$ns duplex-link $n0 $n2 10Mb 10ms DropTail
$ns duplex-link $n0 $n4 10Mb 10ms DropTail
$ns duplex-link $n1 $n3 10Mb 10ms DropTail
$ns duplex-link $n1 $n5 10Mb 10ms DropTail
$ns queue-limit $n0 $n1 20
$ns queue-limit $n1 $n0 20


#set up the tcp connection
set tcp1 [new Agent/TCP]
$ns attach-agent $n3 $tcp1
set sink1 [new Agent/TCPSink]
$ns attach-agent $n2 $sink1
$ns connect $tcp1 $sink1
$tcp1 set fid_ 1
$tcp1 set window_ 80

#setting up the ftp over tcp connection
set ftp1 [new Application/FTP]
$ftp1 attach-agent $tcp1

# Pareto random number generator for HTML file package sizes
set paretoGenerator [new RandomVariable/Pareto]
$paretoGenerator set avg_ 150000
$paretoGenerator set shape_ 1.5

proc getPackageSize {} {
	return $paretoGenerator value
}

# Exponential random number generator for interval sizes
set expoGenerator [new RandomVariable/Exponential]
$expoGenerator set avg_ 0.05

proc getIntervalSize {} {
	return $expoGenerator value
}

# Create 120 FTP connections
set startTime 5
for {set i 1} {$i <= 120} {incr i} {

	# setting up an TCP connection
	set tcpCollection($i) [new Agent/TCP]
	$ns attach-agent $n5 $tcpCollection($i)
	set sink [new Agent/TCPSink]
	$ns attach-agent $n4 $sink
	$ns connect $tcpCollection($i) $sink
	$tcpCollection($i) set fid_ 2
	$tcpCollection($i) set window_ 80

	set ftpCollection($i) [new Application/FTP]
	$ftpCollection($i) attach-agent $tcpCollection($i)

	# Dirty hops.
	if {$i == 40} {
		set startTime 10
	}
	if {$i == 80} {
		set startTime 15
	}
	# and timing...
	set increment [$expoGenerator value]
	set fileSize [$paretoGenerator value]
	set startTime [expr $startTime + $increment]
	$ns at $startTime "$ftpCollection($i) send $fileSize"
}

set winfile [open WinFile w]

#procedure for plotting window size
proc plotWindow {tcpSource file} {
	global ns
	set time 0.1
	set now [$ns now]
	set cwnd [$tcpSource set cwnd_]
	puts $file "$now $cwnd"
	$ns at [expr $now+$time] "plotWindow $tcpSource $file"
}
$ns at 0.1 "plotWindow $tcp1 $winfile"

proc plotThreshold {tcpSource file} {
	global ns
	set time 0.1
	set now [$ns now]
	set ssthresh [$tcpSource set ssthresh_]
	puts $file "$now $ssthresh"
	$ns at [expr $now + $time] "plotThreshold $tcpSource $file"
}
$ns at 0.1 "plotThreshold $tcp1 $winfile"

#schedule events
$ns at 0.0 "$ftp1 start"
$ns at 20.0 "$ftp1 stop"
$ns at 20.0 "finish"
$ns run		
