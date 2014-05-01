# Exercise 1: Bandwidth restrictions on KotNet

# Create simulator
set ns [new Simulator]

# Define different colors for data flows (for NAM)
$ns color 1 Blue
$ns color 2 Red

# Trace file
set tf [open oef1.out.tr w]
$ns trace-all $tf

# NAM tracefile
set nf [open oef1.out.nam w]
$ns namtrace-all $nf

proc finish {} {
        #finalize trace files
        global ns nf tf
        $ns flush-trace
        close $tf
        close $nf

        exec nam oef1.out.nam &
        exit 0
}

# Create seven nodes
set n0 [$ns node]
set n1 [$ns node]
set n2 [$ns node]
set n3 [$ns node]
set n4 [$ns node]
set n5 [$ns node]
set n6 [$ns node]
set n7 [$ns node]

# And links
$ns duplex-link $n0 $n2 10Mb 0.2ms DropTail
$ns duplex-link $n1 $n2 10Mb 0.2ms DropTail
$ns duplex-link $n2 $n3 10Mb 0.2ms DropTail
$ns simplex-link $n3 $n4 256kb 0.2ms DropTail           #Upload
$ns simplex-link $n4 $n3 4Mb 0.2ms DropTail             #Download
$ns duplex-link $n4 $n5 100Mb 0.3ms DropTail
$ns duplex-link $n5 $n6 100Mb 0.3ms DropTail
$ns duplex-link $n5 $n7 100Mb 0.3ms DropTail

# Give node position (for NAM)
#$ns duplex-link-op $n1 $n2 orient right-down
#$ns duplex-link-op $n0 $n2 orient right-up
#$ns duplex-link-op $n2 $n3 orient right
#$ns simplex-link-op $n3 $n4 orient right
#$ns simplex-link-op $n4 $n3 orient right
#$ns duplex-link-op $n4 $n5 orient right
#$ns duplex-link-op $n5 $n6 orient right-up
#$ns duplex-link-op $n5 $n7 orient right-down

# FTP Connection -- (Downloading data from a server within the KU Leuven network)
# Put start and end time between nodes in an array
set ftpConnection(origin) $n6
set ftpConnection(destination) $n1
set ftpConnection(start) 0.1
set ftpConnection(stop) 9.9

# Setting winfile
set wf [open exercice1.wf w]

# Setup a TCP Connection
set tcp [new Agent/TCP]
$ns attach-agent $ftpConnection(origin) $tcp
set sink [new Agent/TCPSink]
$ns attach-agent $ftpConnection(destination) $sink
$ns connect $tcp $sink
$tcp set fid_ 1
$tcp set packetSize_ 1000       #default packagesize
$tcp set window_ 80

# Setup a FTP over TCP connection
set ftp [new Application/FTP]
$ftp attach-agent $tcp


# UDP Connection -- (Uploading data to a server on the Internet)
# Put start and end time between nodes in an array
set udpConnection(origin) $n0
set udpConnection(destination) $n7
set udpConnection(start) 3.0
set udpConnection(stop) 6.0

# Generating CBR Connection
set udp [new Agent/UDP]
$ns attach-agent $udpConnection(origin) $udp
set null [new Agent/Null]
$ns attach-agent $udpConnection(destination) $null
$ns connect $udp $null
$udp set fid_ 2

# Setup a CBR over UDP connection
set cbr [new Application/Traffic/CBR]
$cbr attach-agent $udp
$cbr set packetSize_ 1500
$cbr set rate_ 10.0Mb
$cbr set random_ false

# Window plotting
$ns at 0.1 "plotWindow $tcp $wf"

# Timimg
$ns at $ftpConnection(start) "$ftp start"
$ns at $udpConnection(start) "$cbr start"
$ns at $ftpConnection(stop) "$ftp stop"
$ns at $udpConnection(stop) "$cbr stop"

# Procedure for plotting window size
proc plotWindow {tcpSource file} {
        global ns
        set time 0.1
        set now [$ns now]
        set cwnd [$tcpSource set cwnd_]
        puts $file "$now $cwnd"
        $ns at [expr $now+$time] "plotWindow $tcpSource $file"
}

$ns at 10.0 "finish"

$ns run
