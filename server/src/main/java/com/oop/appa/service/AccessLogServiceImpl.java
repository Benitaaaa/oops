package com.oop.appa.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.oop.appa.dao.AccessLogRepository;
import com.oop.appa.entity.AccessLog;

@Service
public class AccessLogServiceImpl implements AccessLogService {

    private AccessLogRepository accessLogRepository;

    @Autowired
    public AccessLogServiceImpl(AccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    // GET
    @Override
    public List<AccessLog> findAll() {
        try {
            return accessLogRepository.findAll();
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all AccessLogs service：" + e.getMessage(), e);
        }
    }

    @Override
    public Page<AccessLog> findAllPaged(Pageable pageable) {
        try {
            return accessLogRepository.findAll(pageable);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all AccessLogs with pagination service: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AccessLog> findByUserId(Integer user_id) {
        try {
            return accessLogRepository.findByUserId(user_id);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Illegal argument", e);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all AccessLogs with pagination service: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<AccessLog> findByUserIdPaged(Integer user_id, Pageable pageable) {
        try {
            return accessLogRepository.findByUserId(user_id, pageable);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Illegal argument", e);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all AccessLogs with pagination service: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<AccessLog> findByUserIdAndPortfolioIdPaged(Integer userId, Integer portfolioId, Pageable pageable) {
        try {
            List<AccessLog> accessLogs = findByUserId(userId);
            List<AccessLog> filteredAccessLogs = accessLogs.stream()
                    .filter(accessLog -> accessLog.getAction().contains("Portfolio #" + portfolioId))
                    .collect(Collectors.toList());
            long startItem = pageable.getOffset();
            long endItem = Math.min(startItem + pageable.getPageSize(), filteredAccessLogs.size());

            List<AccessLog> pagedAccessLogs;
            if (startItem <= endItem) {
                pagedAccessLogs = filteredAccessLogs.subList((int) startItem, (int) endItem);
            } else {
                pagedAccessLogs = Collections.emptyList();
            }
            return new PageImpl<>(pagedAccessLogs, pageable, filteredAccessLogs.size());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Illegal argument", e);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching AccessLogs with pagination service: " + e.getMessage(), e);
        }
    }

    // POST and UPDATE
    @Override
    public AccessLog save(AccessLog accessLog) {
        try {
            return accessLogRepository.save(accessLog);
        } catch (Exception e) {
            throw new RuntimeException("Error saving AccessLog service: " + e.getMessage(), e);
        }
    }

    // DELETE
    @Override
    public void delete(AccessLog entity) {
        try {
            accessLogRepository.delete(entity);
            return;
        } catch (Exception e) {
            throw new RuntimeException("Error deleting AccessLog service: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(Integer id) {
        try {
            accessLogRepository.deleteById(id);
            return;
        } catch (Exception e) {
            throw new RuntimeException("Error deleting AccessLog by id service: " + e.getMessage(), e);
        }
    }

}
