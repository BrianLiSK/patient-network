/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.data.similarity.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;
import org.slf4j.Logger;

/**
 * Implementation of {@link FeatureSimilarityView} that uses a mutual information metric to score similar patients.
 * 
 * @version $Id$
 * @since
 */
public class MutualInformationPatientSimilarityView extends RestrictedPatientSimilarityView
{
    /*
     * (non-Javadoc)
     * 
     * New components need to be declared in: src/main/resources/META-INF/components.txt
     * PatientSimilarityView injected in similarity-search/.../internal/SolrSimilarPatientsFinder.java, 
     *   instantiated by RestPatSimViewFactory
     * 
     * -x improves bound on cond prob
     * code_to_term: mapping from HP id (str) to node.Term()
     * Term() has .parents (Terms) and .children (Terms)
     *   .dir_freq
     *   .raw_freq - total number of annotations for a term, weighted by probs (+0.001 smoothing)
     *   .freq
     *   .get_ancestors (set of all ancestor nodes)
     *   .patients - set of Patient objects
     *   .inf (set in parse_diseases.compute_probs)
     *     
     * create phenotype.Patient() with list of HP terms (translated using code_to_term)
     *   Patient() inherits from Phenotype():
     *     contains .traits (list of HP terms), .disease (defaults to None)
     *     .all_traits is the set of all traits the patient has (and their ancestors)
     *     Patient object added to a.patients for all ancestors a
     *     
     * call sim.sim on (Patient(), Patient(), x)
     *   get intersection of the sets of all ancestors of all terms for each patient
     *   t.p1count, .p2count, number of terms in each patient that descend from t, with .mincount as min
     *   get cost of each patient's traits
     *     cost of list of Traits is sum_t(t.inf)
     *   shared cost is sum of scaled (* mincount) t.cond_inf for each shared ancestor Term
     *   return 2 * (sum of shared costs) / (sum of separate costs)
     *   
     * parse_diseases uses phenotype_annotation.tab (OMIM) to set Term() properties
     *   OMIM line maps disease to phenotype with rough frequency (different for different dbs)
     *   Disease().freqs(num)/traits(Term)/codes(str) appended
     *   merge any redundant disease traits: append average (else 0.25 if no data) to Disease.freqs
     *     add frequency to trait.raw_freq (aggr across diseases)
     *   t.freq is normalized t.raw_freq, then bounded by [1e-9, 1-1e-9]
     *   t.prob is sum of t.freq for t and all descendants, then bounded by [1e-9, 1-1e-9]
     *   t.inf is -log(t.prob)
     *   t.cond_inf is: t.inf - -logprob(sum of .freq for all common descendants of all strict ancestors of t)
     *     worse approx: t.inf - max(p.inf for parent p of t)
     *   
     * phenotips/resources/solr-configuration/src/main/resources/omim/conf/schema.xml
     */

    /** Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf). */
    private static Map<OntologyTerm, Double> parentCondIC;

    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<OntologyTerm, Double> termIC;

    /** The matched patient to represent. */
    private final Patient match;

    /** The reference patient against which to compare. */
    private final Patient reference;

    /** Provides access to the term ontology. */
    private final OntologyManager ontologyManager;

    /** Logging helper object. */
    private final Logger logger;

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @param ontologyManager the term ontology
     * @param logger logging helper object
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public MutualInformationPatientSimilarityView(Patient match, Patient reference, AccessType access,
        OntologyManager ontologyManager, Logger logger) throws IllegalArgumentException
    {
        super(match, reference, access);
        this.match = match;
        this.reference = reference;
        this.ontologyManager = ontologyManager;
        this.logger = logger;
    }

    /**
     * Set the static information content map for the class.
     * 
     * @param ics the information content of each term
     */
    public static void setICs(Map<OntologyTerm, Double> ics)
    {
        MutualInformationPatientSimilarityView.termIC = ics;
    }

    /**
     * Set the static conditional information content map for the class.
     * 
     * @param condICs the conditional information content of each term, given its parents
     */
    public static void setConditionalICs(Map<OntologyTerm, Double> condICs)
    {
        MutualInformationPatientSimilarityView.parentCondIC = condICs;
    }

    /**
     * Return a (potentially empty) collection of terms present in the patient.
     * 
     * @param patient
     * @return a collection of terms present in the patient
     */
    private Collection<OntologyTerm> getPresentPatientTerms(Patient patient)
    {
        Set<OntologyTerm> terms = new HashSet<OntologyTerm>();
        for (Feature feature : patient.getFeatures()) {
            if (!feature.isPresent()) {
                continue;
            }

            OntologyTerm term = this.ontologyManager.resolveTerm(feature.getId());
            if (term == null) {
                logger.error("Error resolving term: " + feature.getId() + " " + feature.getName());
            } else {
                terms.add(term);
            }
        }
        return terms;
    }

    /**
     * Return the set of terms that occur in the patient's features (and their ancestors).
     * 
     * @param patient
     * @return all OntologyTerms that are implied by the patient's phenotypes
     */
    private Set<OntologyTerm> getAncestors(Patient patient)
    {
        Set<OntologyTerm> ancestors = new HashSet<OntologyTerm>();
        Collection<OntologyTerm> terms = getPresentPatientTerms(patient);
        // Add directly-specified terms
        ancestors.addAll(terms);
        for (OntologyTerm term : terms) {
            // Add all ancestors
            ancestors.addAll(term.getAncestors());
        }
        return ancestors;
    }

    /**
     * Return the information content of term, falling back to parents as necessary.
     * 
     * @param term the term to get the information content of
     * @return the information content of the term, or null if the term was null, or 0.0 if the term had no information
     *         and no parents
     */
    @SuppressWarnings("unused")
    private Double getTermIC(OntologyTerm term)
    {
        if (term == null) {
            return null;
        }
        Double ic = termIC.get(term);
        if (ic == null) {
            ic = 0.0;
            Collection<OntologyTerm> parents = term.getParents();
            if (!parents.isEmpty()) {
                // Use max IC of parents
                for (OntologyTerm parent : parents) {
                    ic = Math.max(ic, getTermIC(parent));
                }
            }
        }
        return ic;
    }

    /**
     * Return the cost of encoding all the terms (and their ancestors) in a tree together.
     * 
     * @param all terms (and their ancestors) that are present in the patient
     * @return the cost of the encoding all the terms
     */
    private double getJointTermsCost(Collection<OntologyTerm> ancestors)
    {
        double cost = 0;
        for (OntologyTerm term : ancestors) {
            Double ic = parentCondIC.get(term);
            if (ic == null) {
                ic = 0.0;
            }
            logger.debug(String.format("  cost(%s) = %s", term.getId(), ic));
            cost += ic;
        }
        return cost;
    }

    /**
     * Return the cost of encoding all the present phenotypes in the patient.
     * 
     * @param patient
     * @return the cost of the patient
     */
    @SuppressWarnings("unused")
    private double getPatientCost(Patient patient)
    {
        logger.debug("Patient: " + patient.getDocument().getName());
        Collection<OntologyTerm> terms = getAncestors(patient);
        return getJointTermsCost(terms);
    }

    @Override
    public double getScore()
    {
        /* pseudocode:
         *   get intersection of the sets of all ancestors of all terms for each patient
         *   t.p1count, .p2count, number of terms in each patient that descend from t, with .mincount as min
         *   get cost of each patient's traits
         *     cost of list of Traits is sum_t(t.inf)
         *   shared cost is sum of scaled (* mincount) t.cond_inf for each shared ancestor Term   
         *   MI = 2 * (sum of shared costs) / (sum of separate costs)
         *   MI [0, Inf] is large if shared cost is high relative to individual costs
         *   tanh(MI) -> [-1, 1]
         */
        if (match == null || reference == null) {
            return Double.NaN;
        }

        // Get ancestors for both patients
        Set<OntologyTerm> refAncestors = getAncestors(reference);
        Set<OntologyTerm> matchAncestors = getAncestors(match);

        // Compute costs of each patient separately
        double p1Cost = getJointTermsCost(refAncestors);
        double p2Cost = getJointTermsCost(matchAncestors);

        // Score overlapping (min) ancestors
        Set<OntologyTerm> sharedAncestors = refAncestors;
        // warning: Line below destructively modifies refAncestors
        sharedAncestors.retainAll(matchAncestors);
        double sharedCost = getJointTermsCost(matchAncestors);
        double harmonicMeanIC = 2 / (p1Cost / sharedCost + p2Cost / sharedCost);

        return harmonicMeanIC;
    }

}
