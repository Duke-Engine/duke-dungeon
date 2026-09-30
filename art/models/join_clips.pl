# Joins every clip of a .glb into one, named by the second argument: each clip's channels and samplers, the samplers
# renumbered, and nothing else in the file touched -- the binary chunk goes back exactly as it came. For a model whose
# parts were animated one clip each in Blender (the keep's gate: a clip for each leaf), so that one clip plays them
# all.
#
#   perl art/models/join_clips.pl src/main/resources/models/props/gate/gate.glb open
use strict;
use warnings;
use JSON::PP;

my ($path, $name) = @ARGV;
die "usage: join_clips.pl model.glb clip-name\n" unless defined $name;
open(my $in, "<:raw", $path) or die "cannot read $path\n";
my $all = do { local $/; <$in> };
close $in;
my ($magic, $version) = unpack("A4V", substr($all, 0, 8));
die "$path is not a glb\n" unless $magic eq "glTF";
my ($length) = unpack("V", substr($all, 12, 4));
my $gltf = JSON::PP->new->decode(substr($all, 20, $length));
my $rest = substr($all, 20 + $length);
my %joined = (name => $name, channels => [], samplers => []);
for my $clip (@{$gltf->{animations} // []}) {
    my $offset = scalar @{$joined{samplers}};
    push @{$joined{samplers}}, @{$clip->{samplers}};
    push @{$joined{channels}}, map { { %$_, sampler => $_->{sampler} + $offset } } @{$clip->{channels}};
}
$gltf->{animations} = [\%joined];
my $json = JSON::PP->new->canonical->encode($gltf);
$json .= " " x ((4 - length($json) % 4) % 4);
my $glb = pack("A4VV", "glTF", $version, 20 + length($json) + length($rest)) . pack("VA4", length($json), "JSON")
        . $json . $rest;
open(my $out, ">:raw", $path) or die "cannot write $path\n";
print $out $glb;
close $out;
