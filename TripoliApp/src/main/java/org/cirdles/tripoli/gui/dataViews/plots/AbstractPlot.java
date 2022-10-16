/*
 * AbstractRawDataView.java
 *
 * Created Jul 6, 2011
 *
 * Copyright 2006 James F. Bowring and Earth-Time.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.cirdles.tripoli.gui.dataViews.plots;

import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.math.BigDecimal;
import java.util.Formatter;

/**
 * @author James F. Bowring
 */
public abstract class AbstractPlot extends Canvas {
    protected double x;
    protected double y;
    protected double width;
    protected double height;
    protected double[] yAxisData;
    protected double[] xAxisData;
    protected double plotWidth;
    protected double plotHeight;
    protected double topMargin = 0.0;
    protected double leftMargin = 0.0;
    protected double minX;
    protected double maxX;
    protected double minY;
    protected double maxY;

    protected ContextMenu plotContextMenu;
    protected double mouseStartX;
    protected double mouseStartY;
        private final EventHandler<MouseEvent> mousePressedEventHandler = e -> {
        if (mouseInHouse(e.getX(), e.getY())) {
            if (e.isPrimaryButtonDown()) {
                mouseStartX = e.getX();
                mouseStartY = e.getY();
            }
        }
    };
    protected BigDecimal[] ticsX;
    protected BigDecimal[] ticsY;
    protected double displayOffsetX = 0.0;
    protected double displayOffsetY = 0.0;
    protected double zoomChunkX;
    protected double zoomChunkY;
    protected String plotTitle;
    protected String plotAxisLabelX;
    protected String plotAxisLabelY;


    private final EventHandler<ScrollEvent> scrollEventEventHandler = new EventHandler<>() {
        @Override
        public void handle(ScrollEvent event) {
            if (mouseInHouse(event.getX(), event.getY())) {
                zoomChunkX = Math.abs(zoomChunkX) * Math.signum(event.getDeltaY());
                zoomChunkY = Math.abs(zoomChunkY) * Math.signum(event.getDeltaY());
                if (getRangeX_Display() >= zoomChunkX) {
                    minX += zoomChunkX;
                    maxX -= zoomChunkX;
                    minY += zoomChunkY;
                    maxY -= zoomChunkY;

                    calculateTics();
                    repaint();
                }
            }
        }
    };
    private final EventHandler<MouseEvent> mouseDraggedEventHandler = e -> {
        if (mouseInHouse(e.getX(), e.getY())) {
            displayOffsetX = displayOffsetX + (convertMouseXToValue(mouseStartX) - convertMouseXToValue(e.getX()));
            mouseStartX = e.getX();

            displayOffsetY = displayOffsetY + (convertMouseYToValue(mouseStartY) - convertMouseYToValue(e.getY()));
            mouseStartY = e.getY();

            calculateTics();
            repaint();
        }
    };

    private AbstractPlot() {
        super();
    }


    /**
     * @param bounds
     */
    protected AbstractPlot(Rectangle bounds, int leftMargin, int topMargin, String plotTitle, String plotAxisLabelX, String plotAxisLabelY) {
        super(bounds.getWidth(), bounds.getHeight());
        x = bounds.getX();
        y = bounds.getY();
        width = bounds.getWidth();
        height = bounds.getHeight();

        this.leftMargin = leftMargin;
        this.topMargin = topMargin;
        this.plotTitle = plotTitle;
        this.plotAxisLabelX = plotAxisLabelX;
        this.plotAxisLabelY = plotAxisLabelY;

        this.xAxisData = new double[0];
        this.yAxisData = new double[0];
        this.ticsX = new BigDecimal[0];
        this.ticsY = new BigDecimal[0];

        updatePlotSize();

        setupPlotContextMenu();

        this.addEventFilter(ScrollEvent.SCROLL, scrollEventEventHandler);
        this.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedEventHandler);
        this.addEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedEventHandler);
        this.setOnMouseClicked(new MouseClickEventHandler());
    }

    private void setupPlotContextMenu() {
        plotContextMenu = new ContextMenu();
        MenuItem plotContextMenuItem1 = new MenuItem("Restore plot");
        plotContextMenuItem1.setOnAction((mouseEvent) -> {
            refreshPanel(true, true);
        });

        MenuItem plotContextMenuItem2 = new MenuItem("Bring to front");
        plotContextMenuItem2.setOnAction((mouseEvent) -> {
            this.getParent().toFront();
        });

        plotContextMenu.getItems().addAll(plotContextMenuItem1, plotContextMenuItem2);

    }

    /**
     * @param g2d
     */
    protected void paintInit(GraphicsContext g2d) {
        relocate(x, y);
        g2d.clearRect(0, 0, width, height);
    }

    /**
     * @param g2d
     */
    public void paint(GraphicsContext g2d) {
        paintInit(g2d);

        drawBorder(g2d);
        drawPlotLimits(g2d);
        drawAxes(g2d);
        labelAxisX(g2d);
        labelAxisY(g2d);
        showTitle(g2d);
    }

    public void repaint() {
        paint(this.getGraphicsContext2D());
    }

    public void plotData(GraphicsContext g2d) {
    }

    public void calculateTics() {
        ticsX = TicGeneratorForAxes.generateTics(getMinX_Display(), getMaxX_Display(), (int) (plotWidth / 40.0));
        if (ticsX.length == 0) {
            ticsX = new BigDecimal[2];
            ticsX[0] = new BigDecimal(minX);
            ticsX[ticsX.length - 1] = new BigDecimal(maxX);
        }

        ticsY = TicGeneratorForAxes.generateTics(getMinY_Display(), getMaxY_Display(), (int) (plotHeight / 20.0));
        if (ticsY.length == 0) {
            ticsY = new BigDecimal[2];
            ticsY[0] = new BigDecimal(minY);
            ticsY[ticsY.length - 1] = new BigDecimal(maxY);
        }

        zoomChunkX = getRangeX_Display() / 10.0;
        zoomChunkY = getRangeY_Display() / 10.0;
    }

    private void drawAxes(GraphicsContext g2d) {
        g2d.setFill(Paint.valueOf("BLACK"));
        g2d.setLineWidth(0.75);
        Text text = new Text();
        text.setFont(Font.font("SansSerif", 11));
        int textWidth;

        if (ticsY.length > 1) {
            // ticsY
            float verticalTextShift = 3.2f;
            g2d.setFont(Font.font("SansSerif", 10));
            if (ticsY != null) {
                for (BigDecimal bigDecimalTicY : ticsY) {
                    if ((mapY(bigDecimalTicY.doubleValue()) >= topMargin) && (mapY(bigDecimalTicY.doubleValue()) <= (topMargin + plotHeight))) {
                        g2d.strokeLine(
                                leftMargin, mapY(bigDecimalTicY.doubleValue()), leftMargin + plotWidth, mapY(bigDecimalTicY.doubleValue()));
                        // left side
                        text.setText(bigDecimalTicY.toString());
                        textWidth = (int) text.getLayoutBounds().getWidth();
                        g2d.fillText(text.getText(),//
                                leftMargin - textWidth - 2.5f,
                                (float) mapY(bigDecimalTicY.doubleValue()) + verticalTextShift);
                    }
                }
            }
            // ticsX
            if (ticsX != null) {
                for (int i = 1; i < ticsX.length - 1; i++) {
                    try {
                        g2d.strokeLine(
                                mapX(ticsX[i].doubleValue()),
                                topMargin + plotHeight,
                                mapX(ticsX[i].doubleValue()),
                                topMargin + plotHeight + 3);
                        // bottom
                        Formatter fmt = new Formatter();
                        fmt.format("%16.2e", ticsX[i].doubleValue());
                        String xText = fmt.toString().trim();
                        g2d.fillText(xText,
                                (float) mapX(ticsX[i].doubleValue()) - 7f,
                                (float) topMargin + plotHeight + 10);

                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public void showTitle(GraphicsContext g2d) {
        Paint savedPaint = g2d.getFill();
        g2d.setFont(Font.font("SansSerif", 12));
        g2d.setFill(Paint.valueOf("RED"));
        g2d.fillText(plotTitle, leftMargin + 10, topMargin - 5);
        g2d.setFill(savedPaint);
    }

    private void labelAxisX(GraphicsContext g2d) {
        Paint savedPaint = g2d.getFill();
        g2d.setFill(Paint.valueOf("BLACK"));
        g2d.setFont(Font.font("SansSerif", 12));
        Text text = new Text();
        text.setFont(Font.font("SansSerif", 12));
        text.setText(plotAxisLabelX);
        int textWidth = (int) text.getLayoutBounds().getWidth();
        g2d.fillText(text.getText(), leftMargin + (plotWidth - textWidth) / 2.0, plotHeight + 2.0 * topMargin - 2.0);
        g2d.setFill(savedPaint);
    }

    private void labelAxisY(GraphicsContext g2d) {
        Paint savedPaint = g2d.getFill();
        g2d.setFill(Paint.valueOf("BLACK"));
        g2d.setFont(Font.font("SansSerif", 12));
        Text text = new Text();
        text.setFont(Font.font("SansSerif", 12));
        text.setText(plotAxisLabelY);
        int textWidth = (int) text.getLayoutBounds().getWidth();
        g2d.rotate(-90.0);
        g2d.fillText(text.getText(), -(2.0 * topMargin + plotHeight) / 2.0 - textWidth / 2.0, 12);
        g2d.rotate(90.0);
        g2d.setFill(savedPaint);
    }

    private void drawBorder(GraphicsContext g2d) {
        // fill it in
        g2d.setFill(Paint.valueOf("WHITE"));
        g2d.fillRect(0, 0, width + 1, height + 1);

        // draw border
        g2d.setStroke(Paint.valueOf("BLACK"));
        g2d.setLineWidth(1);
        g2d.strokeRect(1, 1, width - 1, height - 1);
    }

    private void drawPlotLimits(GraphicsContext g2d) {
        // border and fill
        g2d.setLineWidth(0.5);
        g2d.setStroke(Paint.valueOf("BLACK"));
        g2d.strokeRect(
                leftMargin,
                topMargin,
                plotWidth,
                plotHeight);
        g2d.setFill(Paint.valueOf("BLACK"));
    }

    /**
     * @param x
     * @return mapped x
     */
    public double mapX(double x) {
        return (((x - getMinX_Display()) / getRangeX_Display()) * plotWidth) + leftMargin;
    }

    /**
     * @param y
     * @return mapped y
     */
    public double mapY(double y) {
        return (((getMaxY_Display() - y) / getRangeY_Display()) * plotHeight) + topMargin;
    }

    /**
     * @param doReScale  the value of doReScale
     * @param inLiveMode the value of inLiveMode
     */
    public void refreshPanel(boolean doReScale, boolean inLiveMode) {
        try {
            preparePanel();
            repaint();
        } catch (Exception ignored) {
        }
    }

    /**
     *
     */
    public abstract void preparePanel();

    /**
     * @return the displayOffsetY
     */
    public double getDisplayOffsetY() {
        return displayOffsetY;
    }

    /**
     * @param displayOffsetY the displayOffsetY to set
     */
    public void setDisplayOffsetY(double displayOffsetY) {
        this.displayOffsetY = displayOffsetY;
    }

    /**
     * @return the displayOffsetX
     */
    public double getDisplayOffsetX() {
        return displayOffsetX;
    }

    /**
     * @param displayOffsetX the displayOffsetX to set
     */
    public void setDisplayOffsetX(double displayOffsetX) {
        this.displayOffsetX = displayOffsetX;
    }

    /**
     * @return minimum displayed x
     */
    public double getMinX_Display() {
        return minX + displayOffsetX;
    }

    /**
     * @return maximum displayed x
     */
    public double getMaxX_Display() {
        return maxX + displayOffsetX;
    }

    /**
     * @return minimum displayed y
     */
    public double getMinY_Display() {
        return minY + displayOffsetY;
    }

    /**
     * @return maximum displayed y
     */
    public double getMaxY_Display() {
        return maxY + displayOffsetY;
    }

    /**
     * @return
     */
    public double getRangeX_Display() {
        return (getMaxX_Display() - getMinX_Display());
    }

    /**
     * @return
     */
    public double getRangeY_Display() {
        return (getMaxY_Display() - getMinY_Display());
    }

    /**
     * @return the yAxisData
     */
    public double[] getyAxisData() {
        return yAxisData.clone();
    }

    /**
     * @return the xAxisData
     */
    public double[] getxAxisData() {
        return xAxisData.clone();
    }

    /**
     * @param x
     * @return
     */
    protected double convertMouseXToValue(double x) {
        return ((x - leftMargin + 2) / plotWidth) //
                * getRangeX_Display()//
                + getMinX_Display();
    }

    /**
     * @param y
     * @return
     */
    protected double convertMouseYToValue(double y) {
        return -1 * (((y - topMargin - 1) * getRangeY_Display() / plotHeight)
                - getMaxY_Display());
    }

    protected boolean mouseInHouse(double sceneX, double sceneY) {
        return ((sceneX >= leftMargin)
                && (sceneY >= topMargin)
                && (sceneY < plotHeight + topMargin - 2)
                && (sceneX < (plotWidth + leftMargin - 2)));
    }

    public void updatePlotSize() {
        this.plotWidth = (int) (width - leftMargin - 10.0);
        this.plotHeight = (int) (height - 2 * topMargin);
    }

    public void setMyWidth(double width) {
        this.width = width;
        setWidth(width);
        updatePlotSize();
    }

    public void setMyHeight(double height) {
        this.height = height;
        setHeight(height);
        updatePlotSize();
    }

    public void setWidthF(double width) {
        this.width = width;
    }

    public void setHeightF(double height) {
        this.height = height;
    }

    private class MouseClickEventHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(MouseEvent mouseEvent) {
            plotContextMenu.hide();

            // new logic may 2021 (SQUID) to allow for multiple selections +++++++++++++++++++++++++++++++++++++++++++++
            // determine if left click or with cmd or with shift
            boolean isShift = mouseEvent.isShiftDown();
            boolean isControl = mouseEvent.isControlDown() || mouseEvent.isMetaDown();
            boolean isPrimary = mouseEvent.getButton().compareTo(MouseButton.PRIMARY) == 0;

            if (mouseInHouse(mouseEvent.getX(), mouseEvent.getY())) {
                if (isPrimary) {
                } else {
                    plotContextMenu.show((Node) mouseEvent.getSource(), Side.LEFT, mouseEvent.getX() - getLayoutX(), mouseEvent.getY() - getLayoutY());
                }
            }

        }
    }


}