package org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.ogTripoliPlots;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import org.cirdles.tripoli.gui.AnalysisManagerCallbackI;
import org.cirdles.tripoli.gui.dataViews.plots.AbstractPlot;
import org.cirdles.tripoli.gui.dataViews.plots.PlotWallPane;
import org.cirdles.tripoli.gui.dataViews.plots.PlotWallPaneOGTripoli;
import org.cirdles.tripoli.gui.dataViews.plots.TripoliPlotPane;
import org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.ogTripoliPlots.sessionPlots.BlockRatioCyclesSessionPlot;
import org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.ogTripoliPlots.sessionPlots.SpeciesIntensitySessionPlot;
import org.cirdles.tripoli.plots.PlotBuilder;
import org.cirdles.tripoli.plots.compoundPlots.BlockRatioCyclesBuilder;
import org.cirdles.tripoli.plots.compoundPlots.BlockRatioCyclesRecord;
import org.cirdles.tripoli.plots.sessionPlots.BlockRatioCyclesSessionBuilder;
import org.cirdles.tripoli.plots.sessionPlots.SpeciesIntensitySessionBuilder;
import org.cirdles.tripoli.sessions.analysis.AnalysisInterface;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmc.AllBlockInitForOGTripoli;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmc.SingleBlockModelRecord;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmc.SingleBlockRawDataSetRecord;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataSourceProcessors.MassSpecOutputSingleBlockRecord;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.detectorSetups.Detector;
import org.cirdles.tripoli.species.IsotopicRatio;

import java.util.*;

import static java.util.Arrays.binarySearch;
import static org.cirdles.tripoli.gui.dataViews.plots.TripoliPlotPane.minPlotHeight;
import static org.cirdles.tripoli.gui.dataViews.plots.TripoliPlotPane.minPlotWidth;
import static org.cirdles.tripoli.sessions.analysis.Analysis.SKIP;

public class OGTripoliViewController {
    public static AnalysisInterface analysis;
    public static AnalysisManagerCallbackI analysisManagerCallbackI;
    private AllBlockInitForOGTripoli.PlottingData plottingData;
    @FXML
    private VBox plotWindowVBox;
    @FXML
    private TabPane plotTabPane;
    @FXML
    private AnchorPane ogtSpeciesIntensitiesPlotAnchorPane;
    @FXML
    private AnchorPane ogtCycleRatioPlotsAnchorPane;

    public void setPlottingData(AllBlockInitForOGTripoli.PlottingData plottingData) {
        this.plottingData = plottingData;
    }

    @FXML
    public void initialize() {
        plotWindowVBox.widthProperty().addListener((observable, oldValue, newValue) -> {
            plotTabPane.setMinWidth((Double) newValue);
            ogtCycleRatioPlotsAnchorPane.setMinWidth((Double) newValue);
            ogtSpeciesIntensitiesPlotAnchorPane.setMinWidth((Double) newValue);
        });

        plotWindowVBox.heightProperty().addListener((observable, oldValue, newValue) -> {
            plotTabPane.setMinHeight(((Double) newValue) - 30.0);
            ogtCycleRatioPlotsAnchorPane.setMinHeight(((Double) newValue) - 65.0);
            ogtSpeciesIntensitiesPlotAnchorPane.setMinHeight(((Double) newValue) - 100.0);
        });

        if (plottingData != null) {
            populatePlots();
        }
    }

    public void populatePlots() {
        plotRatios();
        plotOnPeakIntensities();
    }

    public void plotRatios() {
        ogtCycleRatioPlotsAnchorPane.getChildren().clear();

        PlotWallPane plotsWallPane = PlotWallPane.createPlotWallPane("OGTripoliSession", analysis, null, analysisManagerCallbackI);
        PlotWallPane.menuOffset = 0.0;
        plotsWallPane.setBackground(new Background(new BackgroundFill(Paint.valueOf("LINEN"), null, null)));

        plotsWallPane.prefWidthProperty().bind(ogtCycleRatioPlotsAnchorPane.widthProperty());
        plotsWallPane.prefHeightProperty().bind(ogtCycleRatioPlotsAnchorPane.heightProperty());

        ogtCycleRatioPlotsAnchorPane.getChildren().add(plotsWallPane);
        plotWindowVBox.widthProperty().addListener((observable, oldValue, newValue) -> plotsWallPane.stackPlots());
        plotWindowVBox.heightProperty().addListener((observable, oldValue, newValue) -> plotsWallPane.stackPlots());

        SingleBlockModelRecord[] singleBlockModelRecords = plottingData.singleBlockModelRecords();
        int countOfOnPeakCycles = plottingData.cycleCount();

        boolean[] DUMMY_CYCLES_INCLUDED = new boolean[countOfOnPeakCycles];
        Arrays.fill(DUMMY_CYCLES_INCLUDED, true);

        // build list of ratios to plot
        List<IsotopicRatio> ratiosToPlot = new ArrayList<>();
        for (IsotopicRatio isotopicRatio : analysis.getAnalysisMethod().getIsotopicRatiosList()) {
            if (isotopicRatio.isDisplayed()) {
                ratiosToPlot.add(isotopicRatio);
            }
        }
        for (IsotopicRatio isotopicRatio : analysis.getAnalysisMethod().getDerivedIsotopicRatiosList()) {
            if (isotopicRatio.isDisplayed()) {
                ratiosToPlot.add(isotopicRatio);
                // need to calculate cycle values

            }
        }

        for (IsotopicRatio isotopicRatio : ratiosToPlot) {
            TripoliPlotPane tripoliPlotPane = TripoliPlotPane.makePlotPane(plotsWallPane);

            List<BlockRatioCyclesRecord> blockRatioCyclesRecords = new ArrayList<>();
            for (int blockIndex = 0; blockIndex < singleBlockModelRecords.length; blockIndex++) {
                if (singleBlockModelRecords[blockIndex] != null) {
                    Integer blockID = singleBlockModelRecords[blockIndex].blockID();
                    int blockStatus = analysis.getMapOfBlockIdToProcessStatus().get(blockID);
                    boolean processed = (analysis.getMapOfBlockIdToPlots().get(blockID) != null);
                    blockRatioCyclesRecords.add(BlockRatioCyclesBuilder.initializeBlockCycles(
                            blockID,
                            processed,
                            singleBlockModelRecords[blockIndex].detectorFaradayGain(),
                            singleBlockModelRecords[blockIndex].assembleCycleMeansForRatio(isotopicRatio),
                            singleBlockModelRecords[blockIndex].assembleCycleStdDevForRatio(isotopicRatio),
                            DUMMY_CYCLES_INCLUDED,
                            new String[]{isotopicRatio.prettyPrint()},
                            "Blocks & Cycles by Time",
                            "Ratio",
                            true,
                            blockStatus != SKIP).getBlockCyclesRecord());
                } else {
                    blockRatioCyclesRecords.add(null);
                }
            }
            BlockRatioCyclesSessionBuilder blockRatioCyclesSessionBuilder =
                    BlockRatioCyclesSessionBuilder.initializeBlockRatioCyclesSession(
                            isotopicRatio, blockRatioCyclesRecords,
                            "Blocks & Cycles by Time", "Ratio");
            AbstractPlot plot = BlockRatioCyclesSessionPlot.generatePlot(
                    new Rectangle(minPlotWidth, minPlotHeight), blockRatioCyclesSessionBuilder.getBlockRatioCyclesSessionRecord(), plotsWallPane);

            tripoliPlotPane.addPlot(plot);
            plot.refreshPanel(false, false);

        }
        plotsWallPane.buildToolBar();
        plotsWallPane.buildScaleControlsToolbar();

        plotsWallPane.stackPlots();
    }

    private void plotOnPeakIntensities() {
        ogtSpeciesIntensitiesPlotAnchorPane.getChildren().clear();
        PlotWallPaneOGTripoli plotsWallPane = PlotWallPaneOGTripoli.createPlotWallPane("OGTripoliSession");
        PlotWallPane.menuOffset = 0.0;
        plotsWallPane.setBackground(new Background(new BackgroundFill(Paint.valueOf("LINEN"), null, null)));
        plotsWallPane.setPrefSize(ogtSpeciesIntensitiesPlotAnchorPane.getPrefWidth(), ogtSpeciesIntensitiesPlotAnchorPane.getPrefHeight() + PlotWallPaneOGTripoli.toolBarHeight * 2.0);

        plotsWallPane.prefWidthProperty().bind(ogtSpeciesIntensitiesPlotAnchorPane.widthProperty());
        plotsWallPane.prefHeightProperty().bind(ogtSpeciesIntensitiesPlotAnchorPane.heightProperty());

        ogtSpeciesIntensitiesPlotAnchorPane.getChildren().add(plotsWallPane);

        plotWindowVBox.widthProperty().addListener((observable, oldValue, newValue) -> plotsWallPane.stackPlots());
        plotWindowVBox.heightProperty().addListener((observable, oldValue, newValue) -> plotsWallPane.stackPlots());

        SingleBlockRawDataSetRecord[] singleBlockRawDataSetRecords = plottingData.singleBlockRawDataSetRecords();
        SingleBlockModelRecord[] singleBlockModelRecords = plottingData.singleBlockModelRecords();
        // only plotting onPeaks
        int countOfBlocks = singleBlockRawDataSetRecords.length;
        int countOfSpecies = analysis.getAnalysisMethod().getSpeciesList().size();

        // x-axis = time
        double[] xAxis = analysis.getMassSpecExtractedData().calculateSessionTimes();
        int[] xAxisBlockIDs = analysis.getMassSpecExtractedData().assignBlockIdToSessionTime();

        // alternating faraday, model, pm, and model rows (4-tuple for each species)

        double[][] onPeakDataCounts = new double[countOfSpecies * 4][xAxis.length];
        boolean[][] onPeakDataIncluded = new boolean[countOfSpecies][xAxis.length];
        double[][] onPeakDataAmpResistance = new double[countOfSpecies][xAxis.length];
        double[][] onPeakBaseline = new double[countOfSpecies * 4][xAxis.length];
        double[][] onPeakGain = new double[countOfSpecies * 4][xAxis.length];


        Set<Detector> detectors = analysis.getAnalysisMethod().getSequenceTable().getMapOfDetectorsToSequenceCells().keySet();
        Map<Integer, Double> mapOfOrdinalDetectorsToResistance = new TreeMap<>();
        for (Detector detector : detectors) {
            mapOfOrdinalDetectorsToResistance.put(detector.getOrdinalIndex(), detector.getAmplifierResistanceInOhms());
        }

        Map<Integer, MassSpecOutputSingleBlockRecord> blocksData = analysis.getMassSpecExtractedData().getBlocksData();
        for (int blockIndex = 0; blockIndex < countOfBlocks; blockIndex++) {

            if (singleBlockModelRecords[blockIndex] != null) {
                Integer blockID = singleBlockModelRecords[blockIndex].blockID();
                double[] onPeakTimeStamps = blocksData.get(blockID).onPeakTimeStamps();

                SingleBlockModelRecord singleBlockModelRecord = singleBlockModelRecords[blockIndex];
                int countOfBaselineDataEntries = singleBlockRawDataSetRecords[blockIndex].getCountOfBaselineIntensities();
                int countOfFaradayDataEntries = singleBlockRawDataSetRecords[blockIndex].getCountOfOnPeakFaradayIntensities();

                double[] onPeakModelFaradayData = singleBlockModelRecord.getOnPeakDataModelFaradayArray(countOfBaselineDataEntries, countOfFaradayDataEntries);
                double[] baseLineVector = singleBlockModelRecord.baselineMeansArray();
                double dfGain = singleBlockModelRecord.detectorFaradayGain();
                Map<Integer, Integer> mapDetectorOrdinalToFaradayIndex = singleBlockModelRecord.mapDetectorOrdinalToFaradayIndex();

                SingleBlockRawDataSetRecord.SingleBlockRawDataRecord onPeakFaradayDataSet = singleBlockRawDataSetRecords[blockIndex].onPeakFaradayDataSetMCMC();
                List<Double> intensityAccumulatorList = onPeakFaradayDataSet.intensityAccumulatorList();
                List<Boolean> intensityIncludedAccumulatorList = onPeakFaradayDataSet.intensityIncludedAccumulatorList();
                List<Integer> timeIndexAccumulatorList = onPeakFaradayDataSet.timeIndexAccumulatorList();
                List<Integer> isotopeOrdinalIndexAccumulatorList = onPeakFaradayDataSet.isotopeOrdinalIndicesAccumulatorList();
                List<Integer> detectorOrdinalIndicesAccumulatorList = onPeakFaradayDataSet.detectorOrdinalIndicesAccumulatorList();

                for (int onPeakDataIndex = 0; onPeakDataIndex < intensityAccumulatorList.size(); onPeakDataIndex++) {
                    int timeIndex = timeIndexAccumulatorList.get(onPeakDataIndex);
                    double time = onPeakTimeStamps[timeIndex];
                    int intensitySpeciesIndex = isotopeOrdinalIndexAccumulatorList.get(onPeakDataIndex) - 1;
                    int timeIndx = binarySearch(xAxis, time);
                    onPeakDataCounts[intensitySpeciesIndex * 4][timeIndx] = intensityAccumulatorList.get(onPeakDataIndex);
                    onPeakDataCounts[intensitySpeciesIndex * 4 + 1][timeIndx] = onPeakModelFaradayData[onPeakDataIndex];
                    onPeakDataAmpResistance[intensitySpeciesIndex][timeIndx] = mapOfOrdinalDetectorsToResistance.get(detectorOrdinalIndicesAccumulatorList.get(onPeakDataIndex));
                    onPeakBaseline[intensitySpeciesIndex * 4][timeIndx] = baseLineVector[mapDetectorOrdinalToFaradayIndex.get(detectorOrdinalIndicesAccumulatorList.get(onPeakDataIndex))];
                    onPeakBaseline[intensitySpeciesIndex * 4 + 1][timeIndx] = baseLineVector[mapDetectorOrdinalToFaradayIndex.get(detectorOrdinalIndicesAccumulatorList.get(onPeakDataIndex))];
                    onPeakDataIncluded[intensitySpeciesIndex][timeIndx] = intensityIncludedAccumulatorList.get(onPeakDataIndex);
                }

                double[] onPeakModelPhotoMultiplierData = singleBlockModelRecord.getOnPeakDataModelPhotoMultiplierArray(countOfBaselineDataEntries, countOfFaradayDataEntries);

                SingleBlockRawDataSetRecord.SingleBlockRawDataRecord onPeakPhotoMultiplierDataSet = singleBlockRawDataSetRecords[blockIndex].onPeakPhotoMultiplierDataSetMCMC();
                intensityAccumulatorList = onPeakPhotoMultiplierDataSet.intensityAccumulatorList();
                intensityIncludedAccumulatorList = onPeakPhotoMultiplierDataSet.intensityIncludedAccumulatorList();
                timeIndexAccumulatorList = onPeakPhotoMultiplierDataSet.timeIndexAccumulatorList();
                isotopeOrdinalIndexAccumulatorList = onPeakPhotoMultiplierDataSet.isotopeOrdinalIndicesAccumulatorList();
                detectorOrdinalIndicesAccumulatorList = onPeakPhotoMultiplierDataSet.detectorOrdinalIndicesAccumulatorList();

                for (int onPeakDataIndex = 0; onPeakDataIndex < intensityAccumulatorList.size(); onPeakDataIndex++) {
                    int timeIndex = timeIndexAccumulatorList.get(onPeakDataIndex);
                    double time = onPeakTimeStamps[timeIndex];
                    int intensitySpeciesIndex = isotopeOrdinalIndexAccumulatorList.get(onPeakDataIndex) - 1;
                    int timeIndx = binarySearch(xAxis, time);
                    onPeakDataCounts[intensitySpeciesIndex * 4 + 2][timeIndx] = intensityAccumulatorList.get(onPeakDataIndex);
                    onPeakDataCounts[intensitySpeciesIndex * 4 + 3][timeIndx] = onPeakModelPhotoMultiplierData[onPeakDataIndex];
                    onPeakDataAmpResistance[intensitySpeciesIndex][timeIndx] = mapOfOrdinalDetectorsToResistance.get(detectorOrdinalIndicesAccumulatorList.get(onPeakDataIndex));
                    //TODO: address this: onPeakBaseline is  zero for PM for now
                    onPeakGain[intensitySpeciesIndex * 4 + 2][timeIndx] = dfGain;
                    onPeakGain[intensitySpeciesIndex * 4 + 3][timeIndx] = dfGain;
                    onPeakDataIncluded[intensitySpeciesIndex][timeIndx] = intensityIncludedAccumulatorList.get(onPeakDataIndex);
                }
            }
        }

        PlotBuilder plotBuilder = SpeciesIntensitySessionBuilder.initializeSpeciesIntensitySessionPlot(
                xAxis, xAxisBlockIDs, onPeakDataCounts, onPeakDataIncluded, onPeakDataAmpResistance, onPeakBaseline, onPeakGain, new String[]{"Species Intensity by Session"}, "Time (secs)", "Intensity (counts)");

        TripoliPlotPane tripoliPlotPane = TripoliPlotPane.makePlotPane(plotsWallPane);
        AbstractPlot plot = SpeciesIntensitySessionPlot.generatePlot(new Rectangle(minPlotWidth, minPlotHeight), (SpeciesIntensitySessionBuilder) plotBuilder);
        tripoliPlotPane.addPlot(plot);
        plot.refreshPanel(false, false);

        plotsWallPane.buildOGTripoliToolBar(analysis.getAnalysisMethod().getSpeciesList());
        plotsWallPane.buildScaleControlsToolbar();
        plotsWallPane.stackPlots();
    }
}