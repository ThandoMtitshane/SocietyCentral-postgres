package com.societycentral.service;

import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.repository.HosterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HosterService {

    private final HosterRepository hosterRepository;

    @Autowired
    public HosterService(HosterRepository hosterRepository) {
        this.hosterRepository = hosterRepository;
    }

    public List<Hoster> findAll() {
        return hosterRepository.findAll();
    }

    public Optional<Hoster> findById(HosterId id) {
        return hosterRepository.findById(id);
    }

    public List<Hoster> findHostsForEvent(String eventID) {
        return hosterRepository.findByIdEventID(eventID);
    }

    public List<Hoster> findEventsHostedBySociety(String societyID) {
        return hosterRepository.findByIdSocietyID(societyID);
    }

    public Hoster addHost(Hoster hoster) {
        // TODO: BUSINESS RULE - "Each event must be associated with AT LEAST
        // ONE society (the primary host)." When creating a new event, the
        // primary host's Hoster row (isPrimary = true) should be created
        // alongside the Event itself (see EventService.proposeEvent TODO).
        //
        // TODO: consider whether only one Hoster row per event may have
        // isPrimary = true - if so, validate that here before saving.
        return hosterRepository.save(hoster);
    }

    public void removeHost(HosterId id) {
        // TODO: consider preventing removal of the LAST remaining Hoster
        // row for an event (would violate "at least one society" rule),
        // and/or preventing removal of a society that has already submitted
        // an EventOutcome for this event (EventOutcome FK depends on Hoster).
        hosterRepository.deleteById(id);
    }
}
