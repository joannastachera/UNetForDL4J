import org.datavec.api.io.labels.PathLabelGenerator;
import org.datavec.api.writable.NDArrayWritable;
import org.datavec.api.writable.Writable;
import org.datavec.image.loader.NativeImageLoader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class LabelGenerator implements PathLabelGenerator {
    protected static final Logger log = LoggerFactory.getLogger(LabelGenerator.class);

    String labelsDir;
    private static NativeImageLoader imageLoader = new NativeImageLoader(512, 512, 1);
    File file;
    public LabelGenerator(String path) {
        labelsDir=path;
    }

    public Writable getLabelForPath(String path) {
        // TODO Auto-generated method stub
        String dirName;
        file=new File(path);
        dirName=labelsDir+"/train/labels/"+file.getName();
        try
        {
            INDArray origImg=imageLoader.asMatrix(new File(dirName));
            return new NDArrayWritable(origImg);
        }
        catch(IOException ioe)
        {
            ioe.printStackTrace();
            return null;
        }
    }


    public Writable getLabelForPath(URI uri) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean inferLabelClasses() {
        // TODO Auto-generated method stub
        return false;
    }
}