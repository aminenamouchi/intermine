package org.intermine.bio.web.displayer;

/*
 * Copyright (C) 2002-2011 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.api.InterMineAPI;
import org.intermine.api.mines.FriendlyMineManager;
import org.intermine.api.mines.Mine;
import org.intermine.api.profile.ProfileManager;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.model.bio.Gene;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.util.PropertiesUtil;
import org.intermine.util.StringUtil;
import org.intermine.util.Util;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;
import org.intermine.web.logic.session.SessionMethods;

/**
 * For all friendly mines, query for pathways
 *
 * @author Julie Sullivan
 */
public class MinePathwaysDisplayer extends ReportDisplayer
{


    protected static final Logger LOG = Logger.getLogger(MinePathwaysDisplayer.class);

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public MinePathwaysDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {
        Gene gene = (Gene) reportObject.getObject();
        request.setAttribute("gene", gene);
        Map<String, Set<String>> orthologues = getLocalHomologues(gene);
        FriendlyMineManager linkManager = im.getFriendlyMineManager();
        Collection<Mine> mines = linkManager.getFriendlyMines();
        Map<Mine, String> mineToOrthologues = buildHomologueMap(mines, orthologues);
        request.setAttribute("mines", mineToOrthologues);
        request.setAttribute("minePortals", getMinesSettings(request));
    }

    /* Using the provided list of organisms available in this mine, build list of genes to query
     * in each mine.
     */
    private Map<Mine, String> buildHomologueMap(Collection<Mine> mines,
            Map<String, Set<String>> orthologues) {
        Map<Mine, String> mineToOrthologues = new HashMap<Mine, String>();
        // for each mine,
        for (Mine mine : mines) {
            // organism(s) available in mine
            Set<String> remoteMineOrganisms = mine.getDefaultValues();
            StringBuffer genes = new StringBuffer();
            // loop through all of the orthologues available in local mine.  on match, copy over
            for (Map.Entry<String, Set<String>> entry : orthologues.entrySet()) {
                // this mine has genes for these organisms, put in list
                if (remoteMineOrganisms.contains(entry.getKey())) {
                    // flatten so we can use array in js
                    if (genes.length() > 0) {
                        genes.append(",");
                    }
                    genes.append(StringUtil.join(entry.getValue(), ","));
                }
            }
            if (genes.length() > 0) {
                mineToOrthologues.put(mine, genes.toString());
            }
        }
        return mineToOrthologues;
    }

    /**
     * Generate mines settings (colors) from Properties
     * @param request
     * @return
     */
    private HashMap<String, LinkedHashMap<String, String>> getMinesSettings(
            HttpServletRequest request) {
        Properties webProperties = SessionMethods.getWebProperties(request.getSession()
                .getServletContext());
        String localMineName = webProperties.getProperty("project.title");
        Properties props = PropertiesUtil.stripStart("intermines",
                PropertiesUtil.getPropertiesStartingWith("intermines", webProperties));
        Enumeration<?> propNames = props.propertyNames();
        HashMap<String, LinkedHashMap<String, String>> minePortals =
                new HashMap<String, LinkedHashMap<String, String>>();
        while (propNames.hasMoreElements()) {
            String mineId = (String) propNames.nextElement();
            mineId = mineId.substring(0, mineId.indexOf("."));
            Properties mineProps = PropertiesUtil.stripStart(mineId,
                    PropertiesUtil.getPropertiesStartingWith(mineId, props));

            // get name and url
            String mineName = mineProps.getProperty("name");
            String mineURL = mineProps.getProperty("url");
            if (StringUtils.isNotEmpty(mineName) && StringUtils.isNotEmpty(mineURL)
                    && !mineName.equals(localMineName)) {
                LinkedHashMap<String, String> mineDetails = new LinkedHashMap<String, String>();
                // colors for the mines
                String mineBgColor = mineProps.getProperty("bgcolor");
                String mineFrontColor = mineProps.getProperty("frontcolor");
                if (StringUtils.isNotEmpty(mineBgColor)
                        && StringUtils.isNotEmpty(mineFrontColor)) {
                    mineDetails.put("bgcolor", mineBgColor);
                    mineDetails.put("frontcolor", mineFrontColor);
                }
                mineDetails.put("url", mineURL);
                minePortals.put(mineName, mineDetails);
            }
        }
        return minePortals;
    }

    private PathQuery getQuery(Gene gene) {
        PathQuery q = new PathQuery(im.getModel());
        q.addViews("Gene.homologues.homologue.primaryIdentifier",
                "Gene.homologues.homologue.secondaryIdentifier",
                "Gene.homologues.homologue.organism.shortName");
        q.addConstraint(Constraints.eq("Gene.primaryIdentifier", gene.getPrimaryIdentifier()));
        q.addOrderBy("Gene.homologues.homologue.organism.shortName", OrderDirection.ASC);
        return q;
    }

    private Map<String, Set<String>> getLocalHomologues(Gene gene) {
        Map<String, Set<String>> orthologues = new HashMap<String, Set<String>>();
        ProfileManager profileManager = im.getProfileManager();
        PathQueryExecutor executor = im.getPathQueryExecutor(profileManager.getSuperuserProfile());
        PathQuery q = getQuery(gene);
        if (!q.isValid()) {
            return Collections.emptyMap();
        }
        ExportResultsIterator it = executor.execute(q);
        while (it.hasNext()) {
            List<ResultElement> row = it.next();
            String identifier = (String) row.get(0).getField();
            String secondaryIdentifier = (String) row.get(1).getField();
            String organism = (String) row.get(2).getField();
            if (!StringUtils.isEmpty(identifier)) {
                Util.addToSetMap(orthologues, organism, identifier);
            } else if (!StringUtils.isEmpty(secondaryIdentifier)) {
                Util.addToSetMap(orthologues, organism, secondaryIdentifier);
            }
        }
        return orthologues;
    }
}