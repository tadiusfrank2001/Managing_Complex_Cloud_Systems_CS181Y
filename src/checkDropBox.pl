#!/usr/bin/perl -w
#
# checkDropBox.pl - script to check the photoprism "drop" directory
# for any zip, jpeg, or fits images that may have appeared there, and
# add them to the database.  Expects a file named .dropusermap in the drop
# directory, and appends to .droplog in the same place.

use strict;

$ENV{CLASSPATH} = "/home/mikey/photo/war/WEB-INF/classes"
                . ":/usr/local/share/java/classes/postgresql.jar";

my $debug = 0;
my $dropdir = "/var/photodrop";
my @files;
my %uids;
my $x; # trash variable

chdir $dropdir or die("can't chdir to $dropdir: $!");
opendir DROPDIR, "." or die("something is bork: $!");
@files = readdir DROPDIR;
closedir DROPDIR;

while (@files) {
    my $file = shift @files;
    next unless (is_interesting($file));
    read_usermap() unless (%uids);

    # figure out who the file belongs to
    ($x, $x, $x, $x, my $uid, $x, $x, $x, $x, my $mtime) = stat $file;
    unless ($uids{$uid}) {
	log_msg("skipping $file: unknown owner $uid");
	system("mv $file $file.skipped") and warn("can't move $file: $?");
	next;
    }

    # crudely try to avoid concurrency issues (e.g. processing an image
    # that is still in the process of being uploaded) by ignoring any
    # files that have been written in the last 5 minutes
    unless (time() - $mtime > 300) {
	log_msg("skipping $file which isn't old enough");
	next;
    }

    # try to call the ImageInserter on it (the ImageInserter is magic
    # and can tell whether it is a single image, archive, or directory)
    my $cmd = "java net.photoprism.ImageInserter $file $uids{$uid}";
    if ($debug) { print "$cmd\n"; next; }

    log_msg("executing: $cmd");
    if (system("$cmd >>.droplog 2>&1") == 0) {
	unlink($file) or warn("can't delete $file: $!");
    } else {
	log_msg("command failed: $!");
	warn("can't run $cmd: $!");
	system("mv $file $file.err") and warn("can't move $file: $?");
    }
}

exit 0;

sub is_interesting
{
    my $file = shift @_;

    # if "file" is actually a directory, read everything in it and append
    # to the current list of files (i.e. breadth first search)
    # 23 Oct 02 MAD: this works, but doesn't, because if somebody else
    # creates a subdirectory in our directory, we can't remove the files
#    if (-d $file) {
#	return 0 if ($file =~ /\.\.?/); # don't search "." or ".." 
#	opendir SUBDIR, $file or warn("can't open subdirectory $file: $!");
#	my @subfiles = readdir SUBDIR;
#	close SUBDIR or warn("can't close subdirectory $file: $!");
#	while (my $subfile = shift @subfiles) {
#	    push @files, "$file/$subfile" ;
#	}
#	return 0; # the directory itself is uninteresting
#    }
    return 0 unless (-f $file);

    # if "file" is actually a file, decide whether it looks like something
    # we are interested in
    $file =~ tr/A-Z/a-z/;
    return 1 if ($file =~ /.jpg$/ || $file =~ /.jpeg$/);
    return 1 if ($file =~ /.fits$/);
    return 1 if ($file =~ /.zip$/ || $file =~ /.tar(.gz)?$/);
    return 0;
}

sub read_usermap
{
    open MAPFILE, "<.dropusermap" or die("can't open .dropusermap: $!");
    while (<MAPFILE>) {
	(my $osname, my $dbuid) = split /:/, $_, 2;
	$uids{getpwnam($osname)} = $dbuid;
    }
    close MAPFILE or die("can't close .dropusermap: $!");
    my $starttime = `date`;
    log_msg($starttime); # indicates the start of processing in the log
}

sub log_msg
{
    my $msg = shift @_;
    if ($debug) { print $msg; return; }
    chomp $msg;
    system("echo \"$msg\" >>.droplog") and warn("can't log $msg: $?");
}
