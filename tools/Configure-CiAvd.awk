# Keep unrelated device profile settings; replace inherited/duplicate CI resource settings.
/^[[:space:]]*(hw\.cpu\.ncore|hw\.keyboard|disk\.dataPartition\.size)[[:space:]]*=/ { next }
{ print }
END {
    print "hw.cpu.ncore=2"
    print "hw.keyboard=no"
    print "disk.dataPartition.size=6G"
}
