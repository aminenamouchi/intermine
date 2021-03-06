package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2012 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.util.FormattedTextParser;
import org.intermine.util.PropertiesUtil;
import org.intermine.util.StringUtil;

/**
 * ID resolver for ZFIN genes.
 *
 * @author Fengyuan Hu
 */
public class ZfinIdentifiersResolverFactory extends IdResolverFactory
{
    protected static final Logger LOG = Logger.getLogger(ZfinIdentifiersResolverFactory.class);

    // data file path set in ~/.intermine/MINE.properties
    // e.g. resolver.zfin.file=/micklem/data/zfin-identifiers/current/ensembl_1_to_1.txt
    private final String propName = "resolver.zfin.file";
    private final String taxonId = "7955";

    private static final String GENE_PATTERN = "ZDB-GENE";
    
    /**
     * Construct without SO term of the feature type.
     * @param soTerm the feature type to resolve
     */
    public ZfinIdentifiersResolverFactory() {
        this.clsCol = this.defaultClsCol;
    }

    /**
     * Construct with SO term of the feature type.
     * @param soTerm the feature type to resolve
     */
    public ZfinIdentifiersResolverFactory(String clsName) {
        this.clsCol = new HashSet<String>(Arrays.asList(new String[] {clsName}));
    }

    /**
     * Build an IdResolver from Entrez Gene gene_info file
     * @return an IdResolver for Entrez Gene
     */
    @Override
    protected void createIdResolver() {
        if (resolver.hasTaxon(taxonId)) {
            return;
        }
        
        try {
            if (!retrieveFromFile(this.clsCol)) {
                Properties props = PropertiesUtil.getProperties();
                String fileName = props.getProperty(propName);

                if (StringUtils.isBlank(fileName)) {
                    String message = "ZFIN gene resolver has no file name specified, set " 
                        + propName + " to the location of the gene_info file.";
                    LOG.warn(message);
                    return;
                }

                try {
                    createFromFile(new BufferedReader(new FileReader(new File(fileName))));
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException("Failed to open ZFIN id mapping file: "
                            + fileName, e);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Error reading from ZFIN id mapping file: "
                            + fileName, e);
                }
                
                try {
                    resolver.writeToFile(new File(ID_RESOLVER_CACHED_FILE_NAME));
                    System.out. println("Written cache file: " + ID_RESOLVER_CACHED_FILE_NAME);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Error writing resolver cache file: "
                            + ID_RESOLVER_CACHED_FILE_NAME, e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createFromFile(BufferedReader reader) throws IOException {

        // data is in format:
        // ZDBID	ID1|ID2
        Iterator<?> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while (lineIter.hasNext()) {
            String[] line = (String[]) lineIter.next();

            if (line.length < 2 || line[0].startsWith("#") || !line[0].startsWith(GENE_PATTERN)) {
                continue;
            }

            String zfinId = line[0];
            String[] synonyms = StringUtil.split(line[1], "\\|");

            resolver.addMainIds(taxonId, zfinId, Collections.singleton(zfinId));
            resolver.addSynonyms(taxonId, zfinId, new HashSet<String>(Arrays.asList(synonyms)));
        }
    }
}
