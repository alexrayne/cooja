The Cooja Network Simulator
===========================

Cooja is an open source network simulator.


this fork Changes vs Contiki-NG 
===========================
+ [fix-compiledialog-command-update] - fixup of compile dialog:
    * better evaluates node source path,
    * smart composing make string

Serial
------------------------------------------------------------------------
+ [split-cooja-dbgio_serial] gives:
    * SerialUI window shows hex and dump of messages, show recv and sent messages
    * Log interface splits from serial one, log messages now provided by yet another 
        stream, that mote directs printf/puts.
    * Logging received from more serial data can be configured. by default - on.
    * provide protection for mote receive buffer overload

+ [contrib/queue-perf] - sim perfomance boost by Matthew Bradbury PR

+ [feature/plugins-timeselec] - fixed select time between windows - timeline, 
        mote output, radio log, etc. Double-click on item select corresponding time everywhere.

+ [tolerant-projects-interfaces] - cooja don't crash when load uncknown interfaces, just warn about them.

+ [feature/radio-recover-interface] - recover radio interface if absent, but firmware use it.
	if forget RadioInterface firmware may hangs in radio-operations blocking.

Radio
------------------------------------------------------------------------
+ [add-radio-visedit-bitrate] - visual edition for contikiRadio bitrate

+ [radio-show-break] - logging when radio device have turns off during receive. strange situation.

TimeLine
------------------------------------------------------------------------
+ [feature/timeline-radioch-colors] - good coloring of radio-chanels in timeline

+ [timeline-dumpevents-bytime] - timeline save events log now ordered by time 
    (was order by mote id)

+ [feature/timeline-event-minwidth] - provide minimal painted width of events in TimeLine.

Crash and Exceptions
------------------------------------------------------------------------
+ [feature/jni-exception-handle] - with support by WITH_COFFEE, provides break execution 
    on mote code exception. 

+ [feature/jni-mote-kill] - when mote hung, it can break it, and coffee shows stack trace

Clock
------------------------------------------------------------------------
+ [feature/clock-mote_resolution] - gives control on simulator clock resolution by mote.
    Handy for sim RTime lowlevel effects

+ [feature/contikiclock-drift] - ContikiClock provides with clockDrift simulation for specified [ppm]



Logs
------------------------------------------------------------------------

+ [fix/moteout_log-filter_slowdown-cache_filter] - mot output slows down simulation, 
    when filter assigned. this PR drop this gears.
    
+ [feature/loglister-filterhistory] - provide histoty interface, and add hystory to mote outpur filter.
