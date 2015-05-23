package org.grobid.trainer;

import org.grobid.core.GrobidModels;

import java.io.File;

/**
 * Created by Niket on 23/05/15.
 */
public class FigureTrainer extends AbstractTrainer {

    public FigureTrainer(GrobidModels model) {
        super(model);
    }

    @Override
    public int createCRFPPData(File corpusPath, File outputFile) {
        return 0;
    }

    @Override
    public int createCRFPPData(File corpusPath, File outputTrainingFile, File outputEvalFile, double splitRatio) {
        return 0;
    }
}
