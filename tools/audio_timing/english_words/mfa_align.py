"""The aligner's `mfa align`, less the analysis it makes of its alignments afterwards (each utterance's
sound level, frame by frame), which isn't needed here and for utterances ten minutes long takes
gigabytes: four at once ran out of memory. Run with the aligner's own Python, given `mfa align`'s
arguments."""
import sys
from montreal_forced_aligner.alignment.pretrained import PretrainedAligner
from montreal_forced_aligner.command_line.mfa import mfa_cli

PretrainedAligner.analyze_alignments = lambda self, *args, **kwargs: None

if __name__ == '__main__':
    sys.argv = ['mfa', 'align', *sys.argv[1:]]
    sys.exit(mfa_cli())
