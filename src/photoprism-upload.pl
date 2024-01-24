#!/usr/bin/perl -w
#
# Based on flickr-upload, created on Sat Sep  4 21:36:32 2004 
# by Marc Nozell (marc@nozell.com)
#
# From flickr-upload:
#   Most of this code is borrowed from the scripts rotate-right/left
#   by these folks by the folks listed below.  It also uses the handy
#   Flickr::Upload module by Christophe Beauregard
#   <christophe.beauregard@sympatico.ca>
#
# From rotate-right/left:
#   some code is borrowed from a script i found on tigert's web site
#   Matt Doller <mdoller@wpi.edu>
#   Alex Bennee <alex@bennee.com> (some additional hacks)
#
# ------------------------------------------------------------------
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Library General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
# 02111-1307, USA. 
# ------------------------------------------------------------------

use strict;
use warnings;

use HTTP::Request::Common;
use LWP::UserAgent;

my $key = '';
my $server = '';
my @filelist;
my $filenum = 0;
my $file = $ENV{HOME} . '/.photoprism-upload';
my $msg;

if (open(CFG, "<$file")) {
    $server = <CFG>;
    $key = <CFG>;
    chomp $server;
    chomp $key;
    close CFG;
} else {
    $msg = "Please create a file $file that contains the server name " .
	"on the first line and the server key on the second line.";
    `gdialog --title "Photoprism Upload" --msgbox "$msg"`;
    exit(1);
}

# prepare the LWP::UserAgent object
my $ua = new LWP::UserAgent;
$ua->agent('photoprism-upload/1.0');
$ua->cookie_jar({});

# test for argv as Nautilus could just use URI's
if (@ARGV) {
    @filelist=@ARGV;
} else {
    #extract files from NAUTILUS_SCRIPT_SELECTED_URIS
    @filelist = split ("\n", $ENV{NAUTILUS_SCRIPT_SELECTED_URIS} );
}

$msg = "Uploading images to $server";
open DIALOG, '|gdialog --gauge "$msg"';

foreach $file (@filelist) {
    $file =~ s|^file://||; # drop any file://
    ++$filenum;
    next unless ($file =~ m/\.jpe?g$/i);
    print DIALOG 100 * $filenum / (scalar(@filelist) + 1);
    my $req = POST("$server/servlet/upload",
		   'Content_Type' => 'form-data',
		   'Content' => [ 'key' => $key,
				  'img' => [$file]]);
    my $res = $ua->request($req);
    unless ($res->is_success()) {
	print "Error uploading $file: " . $req->message();
	last;
    }
    # having trouble?  maybe you want to see the server response
    # print $res->content();
}

close DIALOG;
exit(0);
