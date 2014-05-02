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


#set up the tcp connection
set tcp1 [new Agent/TCP]
$ns attach-agent $n3 $tcp1
set sink1 [new Agent/TCPSink]
$ns attach-agent $n2 $sink1
$ns connect $tcp1 $sink1
$tcp1 set fid_ 1
$tcp1 set windowd_ 80

#setting up the ftp over tcp connection
set ftp1 [new Application/FTP]
$ftp1 attach-agent $tcp1


# FTP Connection -- (Downloading data from a server within the KU Leuven network)
set ftpConnection(origin) $n5
set ftpConnection(destination) $n4

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

# Procedure for setting up a TCP connection
proc setupFTP {} {
	set tcp [new Agent/TCP]
	$ns attach-agent $ftpConnection(origin) $tcp
	set sink [new Agent/TCPSink]
	$ns attach-agent $ftpConnection(destination) $sink
	$ns connect $tcp $sink
	$tcp set fid_ 1
	$tcp set window_ 80

	set ftp [new Application/FTP]
	$ftp attach-agent $tcp

	return $ftp
}

# Create 120 FTP connections
set startTime 5
set startStringPrefix "$ftpCollection("
set startStringSuffix ") send "
for {set i 1} {$i <= 120} {incr i} {
	set ftpCollection(i) setupFTP

	# Dirty hops. Dirty simple, that is!
	if {$i == 40} {
		set startTime 10
	}
	if {$i == 80} {
		set startTime 15
	}
	# and timing...
	set increment getIntervalSize
	set fileSize getPackageSize
	set startTime [expr $startTime + $increment]
	$ns at startTime $startStringPrefix$i$startStringSuffix$fileSize
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
