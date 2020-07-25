import lombok.AllArgsConstructor;
import lombok.Builder;
import org.apache.commons.math3.util.Pair;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.mkl.global.mkl_rt;
import org.datavec.api.io.filters.BalancedPathFilter;
import org.datavec.api.records.listener.impl.LogRecordListener;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.api.writable.Writable;
import org.datavec.image.loader.ImageLoader;
import org.datavec.image.loader.NativeImageLoader;
import org.datavec.image.recordreader.ImageRecordReader;
import org.datavec.image.transform.FlipImageTransform;
import org.datavec.image.transform.ImageTransform;
import org.datavec.image.transform.PipelineImageTransform;
import org.datavec.image.transform.WarpImageTransform;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.transferlearning.FineTuneConfiguration;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.nn.transferlearning.TransferLearningHelper;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.UNet;
import org.deeplearning4j.common.resources.DL4JResources;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.activations.impl.ActivationIdentity;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;
import org.nd4j.linalg.learning.config.AdaDelta;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.lossfunctions.impl.LossBinaryXENT;
import org.nd4j.nativeblas.Nd4jCpu;
import org.slf4j.LoggerFactory;

import ij.IJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.nd4j.evaluation.classification.Evaluation;


import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import static java.lang.Math.toIntExact;
import static org.reflections.util.ConfigurationBuilder.build;

public class UNetImplementation {
    private static final int seed = 1234;
    private WeightInit weightInit = WeightInit.RELU;
    protected static Random rng = new Random(seed);
    protected static int epochs = 1;
    private static int batchSize = 1;

    private static int width = 512;
    private static int height = 512;
    private static int channels = 3;
    public static final String dataPath = "/home/jstachera/Documents/data";

    public static void main(String[] args) throws IOException {
        UNetImplementation uNetImplementation = new UNetImplementation();
        uNetImplementation.importData(0.2);
    }

    public void importData(double proportion) throws IOException {

        LabelGenerator labelMaker = new LabelGenerator(dataPath + "/labels");
        File mainPath = new File(dataPath);
        FileSplit fileSplit = new FileSplit(mainPath, NativeImageLoader.ALLOWED_FORMATS, rng);
        System.out.println(fileSplit.getRootDir());
        int numExamples = Math.toIntExact(fileSplit.length());
        int numLabels = fileSplit.getRootDir().listFiles(File::isDirectory).length;
        BalancedPathFilter pathFilter = new BalancedPathFilter(rng, labelMaker, numExamples, numLabels, 1);
        System.out.println(numLabels);

        InputSplit[] inputSplit = fileSplit.sample(pathFilter, proportion, 1 - proportion);
        InputSplit trainData = inputSplit[0];
        InputSplit testData = inputSplit[1];



        ImageRecordReader rrTrain = new ImageRecordReader(height, width, channels, labelMaker);
        rrTrain.initialize(trainData, null);

        ImageRecordReader rrTest = new ImageRecordReader(height, width, channels, labelMaker);
        rrTest.initialize(testData, null);

        int labelIndex = 1;
        DataSetIterator dataTrainIter = new RecordReaderDataSetIterator(rrTrain, batchSize, labelIndex, labelIndex, true);
        DataSetIterator dataTestIter = new RecordReaderDataSetIterator(rrTest, 1, labelIndex, labelIndex, true);

        DL4JResources.setBaseDownloadURL("https://dl4jdata.blob.core.windows.net/");
        ZooModel zooModel = UNet.builder().build();
        ComputationGraph pretrainedNet = (ComputationGraph) zooModel.initPretrained(PretrainedType.SEGMENT);
        //System.out.println(pretrainedNet.summary());
        NormalizerMinMaxScaler scaler = new NormalizerMinMaxScaler(0, 1);
        scaler.fitLabel(true);
        scaler.fit(dataTrainIter);
        dataTrainIter.setPreProcessor(scaler);
        scaler.fit(dataTestIter);
        dataTestIter.setPreProcessor(scaler);
        System.out.println(pretrainedNet.summary());

        ComputationGraph unetTransfer = new TransferLearning.GraphBuilder(pretrainedNet)
                .setFeatureExtractor("conv2d_23")
                .removeVertexKeepConnections("activation_23")
                .addLayer("activation_23",
                        new CnnLossLayer.Builder(LossFunctions.LossFunction.XENT)
                                .weightInit(WeightInit.RELU)
                                .activation(Activation.SIGMOID).build(), "conv2d_23")
                .build();


        unetTransfer.init();
        System.out.println(unetTransfer.summary());
        unetTransfer.fit(dataTrainIter, epochs);

        DataSet t = dataTestIter.next();
        scaler.revert(t);
        INDArray[] predicted = unetTransfer.output(t.getFeatures());
        INDArray pred = predicted[0].reshape(new int[]{512, 512});
        Evaluation eval = new Evaluation();

        eval.eval(pred.dup().reshape(512 * 512, 1), t.getLabels().dup().reshape(512 * 512, 1));
        System.out.println(eval.stats());
        DataBuffer dataBuffer = pred.data();
        double[] classificationResult = dataBuffer.asDouble();
        ImageProcessor classifiedSliceProcessor = new FloatProcessor(512, 512, classificationResult);
        int j = 0;
        //segmented image instance
        ImagePlus classifiedImage = new ImagePlus("pred" + j, classifiedSliceProcessor);
        IJ.save(classifiedImage, dataPath + "/predictions/"+ j + ".png");
        j++;
    }
    public String toString(ComputationGraph uNet){
        return uNet.summary();
    }
}