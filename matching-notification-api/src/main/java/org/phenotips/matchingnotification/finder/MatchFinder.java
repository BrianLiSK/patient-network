/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.matchingnotification.finder;

import org.phenotips.data.Patient;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Role;

import java.util.List;
import java.util.Set;

/**
 * @version $Id$
 */
@Role
public interface MatchFinder
{
    /**
     * @return a positive number representing the priority of current match finder
     */
    int getPriority();

    /**
     * Finds matches for a given patient updated after the last time {@link #recordStartMatchesSearch()} was run.
     *
     * @param patients List of local patients
     * @param serverIds a list of servers to be used for matches search indicated by their ids.
     * @param onlyUpdatedAfterLastRun if true, only considers patients updated after the last time matcher was run
     * @return list of matches
     */
    List<PatientMatch> findMatches(List<Patient> patients, Set<String> serverIds, boolean onlyUpdatedAfterLastRun);

    /**
     * @param serverId ID of a servers that is currently used for matches search.
     * Record start time for running matches search.
     */
    void recordStartMatchesSearch(String serverId);

    /**
     * @param serverId ID of a servers that is currently used for matches search.
     * Record completed time for running matches search.
     */
    void recordEndMatchesSearch(String serverId);
}
