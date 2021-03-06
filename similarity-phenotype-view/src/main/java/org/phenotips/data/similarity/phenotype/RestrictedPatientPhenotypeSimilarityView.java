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
package org.phenotips.data.similarity.phenotype;

import org.phenotips.data.Feature;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.Collection;
import java.util.Set;

/**
 * Implementation of {@link org.phenotips.data.similarity.FeatureClusterView} that reveals the full patient information
 * if the user has full access to the patient, and only matching reference information for similar features if the
 * patient is matchable.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class RestrictedPatientPhenotypeSimilarityView extends DefaultPatientPhenotypeSimilarityView
{
    /**
     * Constructor passing the matched feature and the reference feature.
     *
     * @param match the features in the matched patient, can be empty
     * @param reference the features in the reference patient, can be empty
     * @param access the access level of the match
     * @throws IllegalArgumentException if match or reference are null
     */
    public RestrictedPatientPhenotypeSimilarityView(Set<? extends Feature> match, Set<? extends Feature> reference,
        AccessType access) throws IllegalArgumentException
    {
        super(match, reference, access);
    }

    @Override
    protected FeatureClusterView createFeatureClusterView(Collection<Feature> match, Collection<Feature> reference,
        AccessType access, VocabularyTerm root)
    {
        return new RestrictedFeatureClusterView(match, reference, access, root);
    }
}
