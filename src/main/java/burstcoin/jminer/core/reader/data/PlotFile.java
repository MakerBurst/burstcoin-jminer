/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 by luxe - https://github.com/de-luxe - BURST-LUXE-RED2-G6JW-H4HG5
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package burstcoin.jminer.core.reader.data;

import nxt.util.Convert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pocminer.generate.MiningPlot;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Plot file.
 */
public class PlotFile {
    private static final Logger LOG = LoggerFactory.getLogger(PlotFile.class);

    // key -> size
    private Map<BigInteger, Long> chunkPartStartNonces;

    private Path filePath;
    private Long chunkPartNonces;
    private int numberOfParts;
    private long numberOfChunks;

    private String filename;
    private long address;
    private BigInteger startnonce;
    private long plots;
    private long staggeramt;

    private long size;

    /**
     * Instantiates a new Plot file.
     *
     * @param filePath        the file path
     * @param chunkPartNonces the chunk part nonces
     */
    public PlotFile(Path filePath, Long chunkPartNonces) {
        this.filePath = filePath;
        this.chunkPartNonces = chunkPartNonces;
        this.filename = filePath.getFileName().toString();
        String[] parts = filename.split("_");
        this.address = Convert.parseUnsignedLong(parts[0]);
        this.startnonce = new BigInteger(parts[1]);
        this.plots = Long.valueOf(parts[2]);
        staggeramt = Long.valueOf(parts[3]);
        this.numberOfParts = calculateNumberOfParts(staggeramt);
        this.numberOfChunks = plots / staggeramt;

        chunkPartStartNonces = new HashMap<>();

        size = numberOfChunks * staggeramt * MiningPlot.PLOT_SIZE;

        if (LOG.isDebugEnabled()) {
            long fileSize = filePath.toFile().length();
            if (fileSize != size) {
                LOG.debug("incomplete plotFile: " + filePath.toString() + " specified size '" + size + " bytes', size '" + fileSize + " bytes'.");
            }
        }

        long chunkPartSize = this.size / numberOfChunks / numberOfParts;
        for (int chunkNumber = 0; chunkNumber < numberOfChunks; chunkNumber++) {
            for (int partNumber = 0; partNumber < numberOfParts; partNumber++) {
                // register a unique key for identification
                BigInteger chunkPartStartNonce = startnonce.add(BigInteger.valueOf(chunkNumber * staggeramt + partNumber * (staggeramt / numberOfParts)));
                Long key = chunkPartStartNonces.put(chunkPartStartNonce, chunkPartSize);
                if (key != null) {
                    LOG.warn("possible overlapping plot-file '" + filePath + "', please check your plots.");
                }
            }
        }
    }

    /**
     * Gets size.
     *
     * @return the size
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets file path.
     *
     * @return the file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Gets filename.
     *
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Gets address.
     *
     * @return the address
     */
    public long getAddress() {
        return address;
    }

    /**
     * Gets startnonce.
     *
     * @return the startnonce
     */
    public BigInteger getStartnonce() {
        return startnonce;
    }

    /**
     * Gets plots.
     *
     * @return the plots
     */
    public long getPlots() {
        return plots;
    }

    /**
     * Gets staggeramt.
     *
     * @return the staggeramt
     */
    public long getStaggeramt() {
        return staggeramt;
    }

    /**
     * Gets number of chunks.
     *
     * @return the number of chunks
     */
    public long getNumberOfChunks() {
        return numberOfChunks;
    }

    /**
     * Gets number of parts.
     *
     * @return the number of parts
     */
    public int getNumberOfParts() {
        return numberOfParts;
    }

    /**
     * Sets number of parts.
     *
     * @param numberOfParts the number of parts
     */
    public void setNumberOfParts(int numberOfParts) {
        this.numberOfParts = numberOfParts;
    }

    /**
     * Gets chunk part start nonces.
     *
     * @return the chunk part start nonces
     */
    public Map<BigInteger, Long> getChunkPartStartNonces() {
        return chunkPartStartNonces;
    }

    // splitting into parts is not needed, but it seams to improve speed and enables us
    // to have steps of nearly same size
    private int calculateNumberOfParts(long staggeramt) {
        int maxNumberOfParts = 100;

        long targetNoncesPerPart = chunkPartNonces != null ? chunkPartNonces : 320000; // 640000 works fine

        // calculate numberOfParts based on target
        int suggestedNumberOfParts = (int) (staggeramt / targetNoncesPerPart) + 1;

        // ensure stagger is dividable by numberOfParts, if not adjust numberOfParts
        while (staggeramt % suggestedNumberOfParts != 0 && suggestedNumberOfParts < maxNumberOfParts) {
            suggestedNumberOfParts += 1;
        }

        // fallback if number of parts could not be calculated in acceptable range
        if (suggestedNumberOfParts >= maxNumberOfParts) {
            suggestedNumberOfParts = (int) Math.floor(Math.sqrt(staggeramt));
            while (staggeramt % suggestedNumberOfParts != 0) {
                suggestedNumberOfParts--;
            }
        }
        return suggestedNumberOfParts;
    }
}
