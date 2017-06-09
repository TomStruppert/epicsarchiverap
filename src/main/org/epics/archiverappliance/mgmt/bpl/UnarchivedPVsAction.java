/*******************************************************************************
 * Copyright (c) 2011 The Board of Trustees of the Leland Stanford Junior University
 * as Operator of the SLAC National Accelerator Laboratory.
 * Copyright (c) 2011 Brookhaven National Laboratory.
 * EPICS archiver appliance is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 *******************************************************************************/
package org.epics.archiverappliance.mgmt.bpl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.epics.archiverappliance.common.BPLAction;
import org.epics.archiverappliance.config.ConfigService;
import org.epics.archiverappliance.config.PVNames;
import org.epics.archiverappliance.config.PVTypeInfo;
import org.epics.archiverappliance.utils.ui.MimeTypeConstants;
import org.json.simple.JSONValue;

/**
 * Given a list of PVs, determine those that are not being archived/have pending requests.
 * Of course, you can use the status call but that makes calls to the engine etc and can be stressful if you are checking several thousand PVs
 * All this does is check the configservice...
 * 
 * @epics.BPLAction - Given a list of PVs, determine those that are not being archived/have pending requests/have aliases.
 * @epics.BPLActionParam pv - A list of pv names. Send as a CSV using a POST or JSON array.
 * @epics.BPLActionEnd
 * 
 * @author mshankar
 *
 */
public class UnarchivedPVsAction implements BPLAction {
	private static final Logger logger = Logger.getLogger(UnarchivedPVsAction.class);

	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp, ConfigService configService) throws IOException {
		logger.info("Determining PVs that are unarchived ");
		LinkedList<String> pvNames = PVsMatchingParameter.getPVNamesFromPostBody(req, configService);
		LinkedList<String> unarchivedPVs = new LinkedList<String>();
		for(String pvName : pvNames) {
			PVTypeInfo typeInfo = null;
			logger.debug("Check for the name as it came in from the user " + pvName);
			typeInfo = configService.getTypeInfoForPV(pvName);
			if(typeInfo != null) continue;
			logger.debug("Check for the normalized name");
			typeInfo = configService.getTypeInfoForPV(PVNames.normalizePVName(pvName));
			if(typeInfo != null) continue;
			logger.debug("Check for aliases");
			String aliasRealName = configService.getRealNameForAlias(PVNames.normalizePVName(pvName));
			if(aliasRealName != null) { 
				typeInfo = configService.getTypeInfoForPV(aliasRealName);
				if(typeInfo != null) continue;
			}
			logger.debug("Check for fields");
			String fieldName = PVNames.getFieldName(pvName);
			if(fieldName != null) { 
				typeInfo = configService.getTypeInfoForPV(PVNames.stripFieldNameFromPVName(pvName));
				if(typeInfo != null) { 
					if(Arrays.asList(typeInfo.getArchiveFields()).contains(fieldName)) continue;
				}
				String fieldAliasRealName = configService.getRealNameForAlias(PVNames.stripFieldNameFromPVName(pvName));
				if(fieldAliasRealName != null) { 
					typeInfo = configService.getTypeInfoForPV(fieldAliasRealName);
					if(typeInfo != null) { 
						if(Arrays.asList(typeInfo.getArchiveFields()).contains(fieldName)) continue;
					}
				}
			}
			// Check for pending requests...
			Set<String> workFlowPVs = configService.getArchiveRequestsCurrentlyInWorkflow();
			if(workFlowPVs.contains(PVNames.normalizePVName(pvName)) || (aliasRealName != null && workFlowPVs.contains(aliasRealName))) continue;
			
			// Think we've tried every possible use cases..
			unarchivedPVs.add(pvName);
		}
		
		
		resp.setContentType(MimeTypeConstants.APPLICATION_JSON);
		try (PrintWriter out = resp.getWriter()) {
			JSONValue.writeJSONString(unarchivedPVs, out);
		}
	}
}
