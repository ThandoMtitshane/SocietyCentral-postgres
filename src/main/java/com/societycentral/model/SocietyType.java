package com.societycentral.model;

/**
 * Broad category a society falls under, used for browsing/filtering and
 * for the recommendation system (e.g. "students who joined other
 * Technology and Innovation societies").
 * <p>
 * OTHER covers any society that doesn't fit the 14 recognised categories.
 */
public enum SocietyType {
    ACADEMIC,
    RELIGIOUS_AND_FAITH_BASED,
    CULTURAL_AND_HERITAGE,
    COMMUNITY_ENGAGEMENT_AND_VOLUNTEER,
    POLITICAL_AND_LEADERSHIP,
    PROFESSIONAL_DEVELOPMENT,
    ENTREPRENEURSHIP_AND_BUSINESS,
    ARTS_AND_CREATIVE,
    ENVIRONMENTAL_AND_SUSTAINABILITY,
    HEALTH_AND_WELLNESS,
    SPORTS_AND_RECREATION,
    TECHNOLOGY_AND_INNOVATION,
    SPECIAL_INTEREST,
    RESIDENCE_BASED,
    INTERNATIONAL_STUDENT,
    OTHER
}
