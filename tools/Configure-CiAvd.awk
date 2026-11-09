# Keep unrelated device profile settings; replace inherited/duplicate CI resource settings.
BEGIN {
    if (guest_ram != "" && guest_ram != "3072") {
        print "Unsupported CI guest RAM override." > "/dev/stderr"
        exit 2
    }
}
/^[[:space:]]*hw\.ramSize[[:space:]]*=/ { if (guest_ram != "") next }
/^[[:space:]]*(hw\.cpu\.ncore|hw\.keyboard|disk\.dataPartition\.size)[[:space:]]*=/ { next }
{ print }
END {
    print "hw.cpu.ncore=2"
    print "hw.keyboard=no"
    print "disk.dataPartition.size=6G"
    if (guest_ram != "") print "hw.ramSize=" guest_ram
}
