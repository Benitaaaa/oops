package com.oop.appa.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.oop.appa.dao.AccessLogRepository;
import com.oop.appa.dao.PortfolioStockRepository;
import com.oop.appa.dto.PortfolioGroupingSummary;
import com.oop.appa.dto.PortfolioStockCreationDTO;
import com.oop.appa.dto.PortfolioStockRebalancingDTO;
import com.oop.appa.dto.RebalancingTargetPercentagesDTO;
import com.oop.appa.entity.AccessLog;
import com.oop.appa.entity.Portfolio;
import com.oop.appa.entity.PortfolioStock;
import com.oop.appa.entity.Stock;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class PortfolioStockServiceImpl implements PortfolioStockService {
    private PortfolioStockRepository portfolioStockRepository;
    private MarketDataService marketDataService;
    private StockService stockService;
    private PortfolioService portfolioService;
    private AccessLogRepository accessLogRepository;

    @Autowired
    public PortfolioStockServiceImpl(PortfolioStockRepository portfolioStockRepository,
            MarketDataService marketDataService,
            StockService stockService, PortfolioService portfolioService,
            AccessLogRepository accessLogRepository) {
        this.portfolioStockRepository = portfolioStockRepository;
        this.marketDataService = marketDataService;
        this.stockService = stockService;
        this.portfolioService = portfolioService;
        this.accessLogRepository = accessLogRepository;
    }

    // GET
    @Override
    public List<PortfolioStock> findAll() {
        try {
            return portfolioStockRepository.findAll();
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all PortfolioStocks service: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<PortfolioStock> findAllPaged(Pageable pageable) {
        try {
            return portfolioStockRepository.findAll(pageable);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all PortfolioStocks with pagination service: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public List<PortfolioStock> findByPortfolioId(Integer portfolio_id) {
        try {
            return portfolioStockRepository.findByPortfolioPortfolioId(portfolio_id);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all PortfolioStocks by Portfolio ID service: " + portfolio_id,
                    e);
        }
    }

    @Override
    public PortfolioStock findByPortfolioIdAndStockSymbol(Integer portfolioId, String stockSymbol) {
        try {
            return portfolioStockRepository.findByPortfolioPortfolioIdAndStockStockSymbol(portfolioId, stockSymbol)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Stock " + stockSymbol + "not found in Portfolio id" + portfolioId));
        } catch (Exception e) {
            throw new RuntimeException("Error fetching PortfolioStock by Portfolio ID: " + portfolioId
                    + " and stock symbol: " + stockSymbol, e);
        }
    }

    // POST
    @Override
    @Transactional
    public PortfolioStock createPortfolioStock(PortfolioStockCreationDTO dto) {
        try {
            // Validate DTO
            if (dto.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity cannot be 0 or negative");
            } else if (dto.getBuyPrice() <= 0) {
                throw new IllegalArgumentException("Buy price cannot be 0 or negative");
            } else if (dto.getBuyDate() == null) {
                throw new IllegalArgumentException("Buy date cannot be null");
            } else if (dto.getBuyDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Buy date cannot be in the future");
            }
            String action;
            Portfolio portfolio = portfolioService.findById(dto.getPortfolioId())
                    .orElseThrow(() -> new EntityNotFoundException("Portfolio not found"));
            Optional<Stock> stockOptional = stockService.findBySymbol(dto.getSymbol());
            Stock stock;
            // check if stock exists in database
            if (stockOptional.isEmpty()) {
                stock = stockService.saveByStockSymbol(dto.getSymbol());
            } else {
                stock = stockOptional.get();
            }
            // check if portfolio stock exists in current portfolio
            PortfolioStock existingPortfolioStock = portfolio.getPortfolioStocks().stream()
                    .filter(ps -> ps.getStock().getStockSymbol().equals(stock.getStockSymbol()))
                    .findFirst()
                    .orElse(null);
            double remainingCapital = portfolio.getRemainingCapital();
            if (existingPortfolioStock != null) {
                if (existingPortfolioStock.getBuyPrice() == dto.getBuyPrice()
                        && existingPortfolioStock.getQuantity() == dto.getQuantity()) {
                    throw new IllegalArgumentException("Stock quantity and price cannot be the same as before");
                }
                double totalCapitalRemainingAfterPurchase = remainingCapital
                        + existingPortfolioStock.getBuyPrice() * existingPortfolioStock.getQuantity()
                        - (dto.getBuyPrice() * dto.getQuantity());
                if (totalCapitalRemainingAfterPurchase < 0) {
                    action = String.format(
                            "User attempted to drop and repurchase stock %s from Portfolio #%d - %s with new price: %.2f and quantity: %d on %s, but insufficient capital",
                            stock.getStockSymbol(), portfolio.getPortfolioId(), portfolio.getName(), dto.getBuyPrice(),
                            dto.getQuantity(), dto.getBuyDate());
                    accessLogRepository.save(new AccessLog(portfolio.getUser(), action));
                    throw new IllegalArgumentException("Insufficient funds to purchase stock");
                }
                existingPortfolioStock.setBuyPrice(dto.getBuyPrice());
                existingPortfolioStock.setQuantity(dto.getQuantity());
                existingPortfolioStock.setBuyDate(dto.getBuyDate());
                portfolio.setRemainingCapital(totalCapitalRemainingAfterPurchase);
                portfolioService.updatePortfolio(portfolio.getPortfolioId(), portfolio);
                action = String.format(
                        "User succesfully dropped and repurchased stock %s in Portfolio #%d - %s with new price: %.2f and quantity: %d on %s",
                        stock.getStockSymbol(), portfolio.getPortfolioId(), portfolio.getName(), dto.getBuyPrice(),
                        dto.getQuantity(), dto.getBuyDate());
                accessLogRepository.save(new AccessLog(portfolio.getUser(), action));
                return portfolioStockRepository.save(existingPortfolioStock);
            } else {
                // check if portfolio has sufficient capital to purchase stock
                double totalCapitalRemainingAfterPurchase = remainingCapital
                        - (dto.getBuyPrice() * dto.getQuantity());
                if (totalCapitalRemainingAfterPurchase < 0) {
                    action = String.format(
                            "User attempted to purchase stock %s in Portfolio #%d - %s with new price: %.2f and quantity: %d on %s, but insufficient capital",
                            stock.getStockSymbol(), portfolio.getPortfolioId(), portfolio.getName(), dto.getBuyPrice(),
                            dto.getQuantity(), dto.getBuyDate());
                    accessLogRepository.save(new AccessLog(portfolio.getUser(), action));
                    throw new IllegalArgumentException("Insufficient funds to purchase stock");
                }
                PortfolioStock portfolioStock = new PortfolioStock();
                portfolioStock.setStock(stock);
                portfolioStock.setBuyPrice(dto.getBuyPrice());
                portfolioStock.setQuantity(dto.getQuantity());
                portfolioStock.setBuyDate(dto.getBuyDate());
                portfolioStock.setPortfolio(portfolio);
                portfolioStock.setBuyDate(dto.getBuyDate());
                portfolio.setRemainingCapital(totalCapitalRemainingAfterPurchase);
                portfolioService.updatePortfolio(portfolio.getPortfolioId(), portfolio);
                action = String.format(
                        "User added new stock %s to Portfolio #%d - %s, with price: %.2f and quantity: %d on %s",
                        stock.getStockSymbol(), portfolio.getPortfolioId(), portfolio.getName(), dto.getBuyPrice(),
                        dto.getQuantity(), dto.getBuyDate());
                accessLogRepository.save(new AccessLog(portfolio.getUser(), action));

                clearPortfolioVolatilityCache(portfolio.getPortfolioId());
                calculatePortfolioAnnualizedVolatility(portfolio.getPortfolioId());
                return portfolioStockRepository.save(portfolioStock);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating PortfolioStock service: " + e.getMessage(), e);
        }
    }

    @Override
    public void clearPortfolioVolatilityCache(Integer portfolioId) {
    }

    @Override
    public void sellPortfolioStock(Integer portfolioId, String stockSymbol, Integer quantity) {
        // add when all stocks sold, delete from portfolio stocks
        try {
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity to sell must be positive");
            }
            PortfolioStock portfolioStock = findByPortfolioIdAndStockSymbol(portfolioId, stockSymbol);
            Portfolio portfolio = portfolioService.findById(portfolioId)
                    .orElseThrow(() -> new EntityNotFoundException("Portfolio not found"));
            if (portfolioStock.getQuantity() < quantity) {
                throw new IllegalArgumentException("Quantity to sell exceeds quantity owned");
            }
            double portfolioStockCurrentPrice = marketDataService.fetchCurrentData(stockSymbol).path("Global Quote")
                    .path("05. price").asDouble();
            portfolio.setRemainingCapital(portfolio.getRemainingCapital() + (portfolioStockCurrentPrice * quantity));
            portfolioStock.setQuantity(portfolioStock.getQuantity() - quantity);
            portfolioService.updatePortfolio(portfolioId, portfolio);
            if (portfolioStock.getQuantity() == 0) {
                portfolioStockRepository.delete(portfolioStock);
            } else {
                portfolioStockRepository.save(portfolioStock);
            }
            String action = String.format("User sells %d shares of stock %s from Portfolio #%d - %s at price",
                    quantity, stockSymbol, portfolioStock.getPortfolio().getPortfolioId(),
                    portfolioStock.getPortfolio().getName(), portfolioStockCurrentPrice);
            accessLogRepository.save(new AccessLog(portfolioStock.getPortfolio().getUser(), action));
            return;
        } catch (Exception e) {
            throw new RuntimeException("Error selling PortfolioStock service: " + e.getMessage(), e);
        }
    }

    // UPDATE
    @Override
    public void save(PortfolioStock stock) {
        try {
            portfolioStockRepository.save(stock);
        } catch (Exception e) {
            throw new RuntimeException("Error saving PortfolioStock service: " + e.getMessage(), e);
        }
    }

    // DELETE
    @Override
    public void delete(PortfolioStock portfolioStock) {
        try {
            PortfolioStock portfolioStockRef = portfolioStockRepository.findById(portfolioStock.getId())
                    .orElseThrow(() -> new EntityNotFoundException("PortfolioStock not found"));
            portfolioStockRepository.delete(portfolioStockRef);
            String action = String.format("User drops stock %s from Portfolio #%d - %s",
                    portfolioStock.getStock().getStockSymbol(), portfolioStock.getPortfolio().getPortfolioId(),
                    portfolioStock.getPortfolio().getName());
            accessLogRepository.save(new AccessLog(portfolioStock.getPortfolio().getUser(), action));
        } catch (Exception e) {
            throw new RuntimeException("Error deleting PortfolioStock service: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByPortfolioIdAndStockSymbol(Integer portfolioId, String stockSymbol) {
        try {
            PortfolioStock portfolioStock = portfolioStockRepository
                    .findByPortfolioPortfolioIdAndStockStockSymbol(portfolioId, stockSymbol)
                    .orElseThrow(() -> new EntityNotFoundException("PortfolioStock not found"));
            Portfolio portfolio = portfolioStock.getPortfolio();
            portfolioStockRepository.delete(portfolioStock);
            portfolio.setRemainingCapital(
                    portfolio.getRemainingCapital() + portfolioStock.getBuyPrice() * portfolioStock.getQuantity());
            portfolioService.updatePortfolio(portfolioId, portfolio);
            String action = String.format("User deletes stock %s from Portfolio #%d - %s", stockSymbol, portfolioId,
                    portfolioStock.getPortfolio().getName());
            accessLogRepository.save(new AccessLog(portfolioStock.getPortfolio().getUser(), action));
        } catch (Exception e) {
            throw new RuntimeException("Error deleting PortfolioStock by Portfolio id service: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(int portfolioStockId) {
        try {
            PortfolioStock portfolioStock = portfolioStockRepository.findById(portfolioStockId)
                    .orElseThrow(() -> new EntityNotFoundException("PortfolioStock not found"));
            portfolioStockRepository.deleteById(portfolioStockId);
            Portfolio portfolio = portfolioStock.getPortfolio();
            portfolio.setRemainingCapital(
                    portfolio.getRemainingCapital() + portfolioStock.getBuyPrice() * portfolioStock.getQuantity());
            String action = String.format("User deletes stock %s from  Portfolio #%d - %s",
                    portfolioStock.getStock().getStockSymbol(), portfolioStock.getPortfolio().getPortfolioId(),
                    portfolioStock.getPortfolio().getName());
            accessLogRepository.save(new AccessLog(portfolioStock.getPortfolio().getUser(), action));
            return;
        } catch (Exception e) {
            throw new RuntimeException("Error deleting PortfolioStock by id service: " + e.getMessage(), e);
        }
    }

    // Other services
    @Override
    public Map<String, Double> calculateWeightedStockReturn(Integer portfolioId, String stockSymbol) {
        Map<String, Double> weightedReturns = new HashMap<>();
        try {
            PortfolioStock stock = findByPortfolioIdAndStockSymbol(portfolioId, stockSymbol);
            double totalReturn = 0;
            int totalQuantity = stock.getQuantity();
            double currentPrice = marketDataService.fetchCurrentData(stockSymbol).path("Global Quote")
                    .path("05. price").asDouble();
            double buyPrice = stock.getBuyPrice();
            double individualReturn = ((currentPrice - buyPrice) / buyPrice) * 100;
            totalReturn += (double) stock.getQuantity() / totalQuantity * individualReturn;

            weightedReturns.put("weightedReturn", totalReturn);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating weighted stock return service: " + e.getMessage(), e);
        }
        return weightedReturns;
    }

    @Override
    public Map<String, Double> calculateStockWeight(Integer portfolioId, String stockSymbol) {
        Map<String, Double> stockWeightResult = new HashMap<>();
        try {
            PortfolioStock currentStock = findByPortfolioIdAndStockSymbol(portfolioId, stockSymbol);
            double currentPrice = marketDataService.fetchCurrentData(stockSymbol).path("Global Quote")
                    .path("05. price").asDouble();
            double stockMarketValue = currentStock.getQuantity() * currentPrice;

            double totalPortfolioValue = 0;
            List<PortfolioStock> allStocksInPortfolio = findByPortfolioId(portfolioId);
            for (PortfolioStock stock : allStocksInPortfolio) {
                double stockPrice = marketDataService.fetchCurrentData(stock.getStockSymbol()).path("Global Quote")
                        .path("05. price").asDouble();
                totalPortfolioValue += stock.getQuantity() * stockPrice;
            }
            double weight = stockMarketValue / totalPortfolioValue;
            stockWeightResult.put(stockSymbol, weight);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating stock weight service: " + e.getMessage(), e);
        }
        return stockWeightResult;
    }

    @Override
    public Map<String, Double> calculateAnnualisedReturn(Integer portfolioStockId, String stockSymbol) {
        Map<String, Double> annualisedReturnResult = new HashMap<>();
        try {
            PortfolioStock stock = portfolioStockRepository
                    .findByPortfolioPortfolioIdAndStockStockSymbol(portfolioStockId, stockSymbol).orElse(null);
            if (stock == null) {
                throw new IllegalArgumentException("PortfolioStock not found for ID: " + portfolioStockId);
            }
            double currentPrice = marketDataService.fetchCurrentData(stock.getStock().getStockSymbol())
                    .path("Global Quote")
                    .path("05. close price").asDouble();
            double buyPrice = stock.getBuyPrice();
            long days = getDaysHeld(portfolioStockId);

            double annualisedReturn = ((Math.pow((currentPrice / buyPrice), (365.0 / days))) - 1) * 100;

            annualisedReturnResult.put("annualisedReturn", annualisedReturn);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating annualised return service: " + e.getMessage(), e);
        }
        return annualisedReturnResult;
    }

    @Override
    public Map<String, Map<String, Double>> calculateStockReturnsForPortfolio(Integer portfolioId) {
        try {
            List<PortfolioStock> allStocksInPortfolio = findByPortfolioId(portfolioId);

            // 1. Calculate the total buy price and total quantity for each unique stock
            Map<String, Double> totalBuyPrices = new HashMap<>();
            Map<String, Double> totalQuantities = new HashMap<>();

            for (PortfolioStock stock : allStocksInPortfolio) {
                String stockSymbol = stock.getStock().getStockSymbol();
                totalBuyPrices.merge(stockSymbol, (double) (stock.getBuyPrice() * stock.getQuantity()), Double::sum);
                totalQuantities.merge(stockSymbol, (double) stock.getQuantity(), Double::sum);
            }

            // 2. Fetch the current stock prices and calculate the actual value and
            // percentage return
            Map<String, Double> currentPrices = new HashMap<>();
            Map<String, Map<String, Double>> returnsByStock = new HashMap<>();

            for (String stockSymbol : totalBuyPrices.keySet()) {
                double currentPrice = marketDataService.fetchCurrentData(stockSymbol)
                        .path("Global Quote")
                        .path("05. price").asDouble();
                currentPrices.put(stockSymbol, currentPrice);

                double aggregatedBuyPrice = totalBuyPrices.get(stockSymbol);
                double aggregatedQuantity = totalQuantities.get(stockSymbol);
                double aggregatedCurrentValue = currentPrice * aggregatedQuantity;

                double actualValue = aggregatedCurrentValue - aggregatedBuyPrice;
                double percentageReturn = (actualValue / aggregatedBuyPrice) * 100;

                // Rounding
                BigDecimal bdActualValue = new BigDecimal(Double.toString(actualValue)).setScale(2,
                        RoundingMode.HALF_UP);
                BigDecimal bdPercentageReturn = new BigDecimal(Double.toString(percentageReturn)).setScale(2,
                        RoundingMode.HALF_UP);

                Map<String, Double> returnDetails = new HashMap<>();
                returnDetails.put("actualValue", bdActualValue.doubleValue());
                returnDetails.put("percentage", bdPercentageReturn.doubleValue());

                returnsByStock.put(stockSymbol, returnDetails);
            }

            return returnsByStock;
        } catch (Exception e) {
            throw new RuntimeException("Error calculating stock returns: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Double> calculateOverallPortfolioReturns(Integer portfolioId) {
        try {
            Map<String, Map<String, Double>> stockReturns = calculateStockReturnsForPortfolio(portfolioId);
            double totalPurchaseValue = 0.0;
            double totalActualReturn = 0.0;

            for (Map.Entry<String, Map<String, Double>> entry : stockReturns.entrySet()) {
                double actualValueForStock = entry.getValue().get("actualValue");

                PortfolioStock stock = findByPortfolioIdAndStockSymbol(portfolioId, entry.getKey());
                double purchaseValue = stock.getBuyPrice() * stock.getQuantity();

                totalPurchaseValue += purchaseValue;
                totalActualReturn += actualValueForStock;
            }

            double totalCurrentValue = totalActualReturn + totalPurchaseValue;
            double overallReturn = totalCurrentValue - totalPurchaseValue;
            double percentageReturn = (overallReturn / totalPurchaseValue) * 100;

            BigDecimal bdPercentageReturn = new BigDecimal(percentageReturn).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bdOverallReturn = new BigDecimal(overallReturn).setScale(2, RoundingMode.HALF_UP);

            Map<String, Double> returns = new HashMap<>();
            returns.put("percentage", bdPercentageReturn.doubleValue());
            returns.put("overalReturn", bdOverallReturn.doubleValue());
            return returns;

        } catch (Exception e) {
            throw new RuntimeException("Error calculating overall portfolio returns: " + e.getMessage(), e);
        }
    }

    @Override
    public PortfolioGroupingSummary calculateTotalPortfolioValueByGroup(Integer portfolioId, String groupBy) {
        try {
            List<PortfolioStock> allStocksInPortfolio = findByPortfolioId(portfolioId);
            Double portfolioRemainingBalance = portfolioService.findById(portfolioId).get().getRemainingCapital();
            Map<String, PortfolioGroupingSummary.StockInfo> portfolioStocksInfo = new HashMap<>();

            for (PortfolioStock stock : allStocksInPortfolio) {
                String stockSymbol = stock.getStock().getStockSymbol();
                if (!portfolioStocksInfo.containsKey(stockSymbol)) {
                    double currentPrice = marketDataService.fetchCurrentData(stockSymbol)
                            .path("Global Quote").path("05. price").asDouble();
                    Stock stockInformation = stockService.findBySymbol(stockSymbol).get();
                    portfolioStocksInfo.put(stockSymbol,
                            new PortfolioGroupingSummary.StockInfo(stock.getQuantity(), currentPrice,
                                    stockInformation.getSector(), stockInformation.getIndustry(),
                                    stockInformation.getExchange(), stockInformation.getCountry()));
                }
            }

            double totalPortfolioStockValue = allStocksInPortfolio.stream()
                    .mapToDouble(stock -> stock.getQuantity()
                            * portfolioStocksInfo.get(stock.getStock().getStockSymbol()).getCurrentPrice())
                    .sum();
            double totalPortfolioValue = totalPortfolioStockValue + portfolioRemainingBalance;

            Function<PortfolioStock, String> groupingFunction = getGroupingFunction(groupBy);
            Map<String, Double> valueByGroup = allStocksInPortfolio.stream()
                    .collect(Collectors.groupingBy(
                            groupingFunction,
                            Collectors.summingDouble(stock -> stock.getQuantity()
                                    * portfolioStocksInfo.get(stock.getStock().getStockSymbol()).getCurrentPrice())));

            PortfolioGroupingSummary summary = new PortfolioGroupingSummary();
            summary.setTotalPortfolioValue(totalPortfolioValue);
            summary.setPortfolioStocks(portfolioStocksInfo);

            Map<String, PortfolioGroupingSummary.Allocation> allocations = new HashMap<>();
            valueByGroup.forEach((key, value) -> allocations.put(key,
                    new PortfolioGroupingSummary.Allocation(value, (value / totalPortfolioValue) * 100)));
            allocations.put("CASH", new PortfolioGroupingSummary.Allocation(portfolioRemainingBalance,
                    (portfolioRemainingBalance / totalPortfolioValue) * 100));
            summary.setAllocations(allocations);

            return summary;

        } catch (Exception e) {
            throw new RuntimeException("Error calculating total portfolio value by group: " + e.getMessage(), e);
        }
    }

    @Override
    public Long getDaysHeld(Integer portfolioStockId) {
        try {
            PortfolioStock stock = portfolioStockRepository.findById(portfolioStockId).orElse(null);
            if (stock == null || stock.getBuyDate() == null) {
                throw new IllegalArgumentException("Invalid PortfolioStock or buy date for ID: " + portfolioStockId);
            }
            LocalDate buyDate = stock.getBuyDate();
            LocalDate currentDate = LocalDate.now();
            return ChronoUnit.DAYS.between(buyDate, currentDate);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating days held service: " + e.getMessage(), e);
        }

    }

    @Override
    public Map<String, Object> getPortfolioSummary(Integer portfolioId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Portfolio portfolio = portfolioService.findById(portfolioId)
                    .orElseThrow(() -> new EntityNotFoundException("Portfolio not found"));
            List<PortfolioStock> portfolioStocks = portfolio.getPortfolioStocks();
            if (portfolioStocks.isEmpty()) {
                response.put("totalPortfolioValue", 0.00);
                response.put("stockReturns", new HashMap<>());
                response.put("overallReturns", new HashMap<>());
                return response;
            }
            double totalPortfolioValue = getTotalPortfolioValue(portfolioId);
            Map<String, Map<String, Double>> stockReturns = calculateStockReturnsForPortfolio(portfolioId);
            Map<String, Double> overallReturns = calculateOverallPortfolioReturns(portfolioId);

            response.put("totalPortfolioValue", totalPortfolioValue);
            response.put("stockReturns", stockReturns);
            response.put("overallReturns", overallReturns);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Error getting portfolio summary service: " + e.getMessage(), e);
        }

    }

    private Function<PortfolioStock, String> getGroupingFunction(String groupBy) {
        switch (groupBy.toLowerCase()) {
            case "sector":
                return stock -> stock.getStock().getSector();
            case "industry":
                return stock -> stock.getStock().getIndustry();
            case "exchange":
                return stock -> stock.getStock().getExchange();
            case "country":
                return stock -> stock.getStock().getCountry();
            default:
                throw new IllegalArgumentException("Unsupported groupBy value: " + groupBy);
        }
    }

    @Override
    public Map<String, Double> calculatePortfolioMonthlyVolatility(Integer portfolioId) {
        Map<String, Double> stockVolatilities = new HashMap<>();
        List<PortfolioStock> allStocksInPortfolio = findByPortfolioId(portfolioId);
        double portfolioVolatility = 0.0;

        for (PortfolioStock stock : allStocksInPortfolio) {
            String stockSymbol = stock.getStockSymbol();
            Map<String, Double> stockMonthlyVolatility = stockService.calculateMonthlyVolatility(stockSymbol);

            Double stockMonthlyVolatilityData = stockMonthlyVolatility.get(stockSymbol);
            Map<String, Double> stockWeight = calculateStockWeight(portfolioId, stockSymbol);
            Double stockWeightData = stockWeight.get(stockSymbol);
            portfolioVolatility += stockWeightData * stockMonthlyVolatilityData;
        }
        stockVolatilities.put("portfolioVolatility", portfolioVolatility);
        return stockVolatilities;
    }

    @Override
    public Map<String, Double> calculatePortfolioAnnualizedVolatility(Integer portfolioId) {
        Map<String, Double> annualizedVolatilities = new HashMap<>();

        Map<String, Double> monthlyVolatilities = calculatePortfolioMonthlyVolatility(portfolioId);
        for (Map.Entry<String, Double> entry : monthlyVolatilities.entrySet()) {
            double annualizedVolatility = entry.getValue() * Math.sqrt(12);
            annualizedVolatilities.put(entry.getKey(), annualizedVolatility);
        }
        return annualizedVolatilities;
    }

    @Override
    public double getTotalPortfolioValue(Integer portfolioId) {
        List<PortfolioStock> allStocksInPortfolio = findByPortfolioId(portfolioId);

        // Fetch the current prices of unique stocks only once to minimize API calls
        Map<String, Double> currentPrices = new HashMap<>();
        for (PortfolioStock stock : allStocksInPortfolio) {
            String stockSymbol = stock.getStock().getStockSymbol();
            if (!currentPrices.containsKey(stockSymbol)) {
                double currentPrice = marketDataService.fetchCurrentData(stockSymbol)
                        .path("Global Quote").path("05. price").asDouble();
                currentPrices.put(stockSymbol, currentPrice);
            }
        }
        // Calculate total portfolio value
        double totalPortfolioValue = allStocksInPortfolio.stream()
                .mapToDouble(stock -> stock.getQuantity() * currentPrices.get(stock.getStock().getStockSymbol()))
                .sum();

        return totalPortfolioValue;
    }

    private Map<String, Double> fetchCurrentPricesForPortfolio(Integer portfolioId) {
        List<PortfolioStock> allStocksInPortfolio = findByPortfolioId(portfolioId);
        Map<String, Double> stockPrices = new HashMap<>();
        for (PortfolioStock stock : allStocksInPortfolio) {
            String stockSymbol = stock.getStockSymbol();
            if (!stockPrices.containsKey(stockSymbol)) {
                double currentPrice = marketDataService.fetchCurrentData(stockSymbol).path("Global Quote")
                        .path("05. price").asDouble();
                stockPrices.put(stockSymbol, currentPrice);
            }
        }
        return stockPrices;
    }

    @Override
    public Map<String, Object> rebalancePortfolio(Integer portfolioId, String rebalancingBy,
            RebalancingTargetPercentagesDTO rebalancingTargetPercentagesDTO) {
        // Fetch current portfolio
        double totalPercentage = 0.0;
        for (Map.Entry<String, Double> entry : rebalancingTargetPercentagesDTO.getTargetPercentages().entrySet()) {
            totalPercentage += entry.getValue();
        }
        if (totalPercentage != 100) {
            throw new IllegalArgumentException("Total allocation doesnt add up to 100%");
        }
        PortfolioGroupingSummary currentPortfolio = calculateTotalPortfolioValueByGroup(portfolioId, rebalancingBy);
        double totalPortfolioValue = currentPortfolio.getTotalPortfolioValue();

        // Initialize maps to store the final allocation values and percentages
        Map<String, Double> finalAllocationValues = new HashMap<>();
        Map<String, Double> finalAllocationPercentages = new HashMap<>();

        // Calculate the target value for each group
        Map<String, Double> groupTargetValues = new HashMap<>();
        for (Map.Entry<String, Double> entry : rebalancingTargetPercentagesDTO.getTargetPercentages().entrySet()) {
            groupTargetValues.put(entry.getKey(), totalPortfolioValue * entry.getValue() / 100.0);
            finalAllocationValues.put(entry.getKey(), 0.0); // Initialize final allocation values
        }

        // Calculate adjustments required for each stock
        Map<String, Double> stockAdjustments = new HashMap<>();
        double projectedTotalPortfolioValue = totalPortfolioValue;

        for (Map.Entry<String, PortfolioGroupingSummary.StockInfo> stockEntry : currentPortfolio.getPortfolioStocks()
                .entrySet()) {
            String stockSymbol = stockEntry.getKey();
            PortfolioGroupingSummary.StockInfo stockInfo = stockEntry.getValue();
            double currentStockValue = stockInfo.getQuantity() * stockInfo.getCurrentPrice();

            // Determine the group of the stock
            String stockGroup;
            if (rebalancingBy.equals("country")) {
                stockGroup = stockInfo.getCountry();
            } else if (rebalancingBy.equals("exchange")) {
                stockGroup = stockInfo.getExchange();
            } else if (rebalancingBy.equals("industry")) {
                stockGroup = stockInfo.getIndustry();
            } else if (rebalancingBy.equals("sector")) {
                stockGroup = stockInfo.getSector();
            } else {
                throw new IllegalArgumentException("Unsupported rebalancingBy value: " + rebalancingBy);
            }
            double groupTargetValue = groupTargetValues.getOrDefault(stockGroup, 0.0);

            // Calculate target stock value based on its proportion in the group
            double stockProportionInGroup = currentStockValue
                    / currentPortfolio.getAllocations().get(stockGroup).getActualValue();
            double targetStockValue = groupTargetValue * stockProportionInGroup;

            // Calculate and store the number of shares to adjust for this stock
            int sharesToAdjust = (int) Math.round((targetStockValue - currentStockValue) / stockInfo.getCurrentPrice());
            stockAdjustments.put(stockSymbol, (double) sharesToAdjust);

            // Update projected portfolio value and final allocation values
            double adjustedStockValue = currentStockValue + (sharesToAdjust * stockInfo.getCurrentPrice());
            projectedTotalPortfolioValue += sharesToAdjust * stockInfo.getCurrentPrice();
            finalAllocationValues.put(stockGroup, finalAllocationValues.get(stockGroup) + adjustedStockValue);
        }

        // Handling cash adjustments
        if (groupTargetValues.containsKey("CASH")) {
            double targetCash = groupTargetValues.getOrDefault("CASH", 0.0);
            double actualCash = currentPortfolio.getAllocations()
                    .getOrDefault("CASH", new PortfolioGroupingSummary.Allocation(0.0, 0.0)).getActualValue();
            double cashAdjustment = targetCash - actualCash;

            stockAdjustments.put("CASH", cashAdjustment);
            double finalCashValue = actualCash + cashAdjustment;
            finalAllocationValues.put("CASH", finalCashValue);
            projectedTotalPortfolioValue = projectedTotalPortfolioValue + cashAdjustment;
        }

        // Calculate the final allocation percentages based on the updated values
        for (Map.Entry<String, Double> finalAllocation : finalAllocationValues.entrySet()) {
            String group = finalAllocation.getKey();
            double value = finalAllocation.getValue();
            finalAllocationPercentages.put(group, (value / projectedTotalPortfolioValue) * 100.0);
        }

        // Return stock adjustments, projected portfolio value, and final allocations

        for (Map.Entry<String, Double> finalAllocation : finalAllocationValues.entrySet()) {
            String group = finalAllocation.getKey();
            double value = finalAllocation.getValue();
            finalAllocationPercentages.put(group, (value / projectedTotalPortfolioValue) * 100.0);
        }

        Map<String, Object> adjustments = new HashMap<>();
        adjustments.put("stockAdjustments", stockAdjustments);
        adjustments.put("projectedTotalPortfolioValue", projectedTotalPortfolioValue);
        adjustments.put("finalAllocations", finalAllocationPercentages.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Map.of("actualValue",
                        finalAllocationValues.get(entry.getKey()), "percentage", entry.getValue()))));

        Map<String, Double> groupTargetValues2 = new HashMap<>();
        for (Map.Entry<String, Double> entry : rebalancingTargetPercentagesDTO.getTargetPercentages().entrySet()) {
            groupTargetValues2.put(entry.getKey(), totalPortfolioValue * entry.getValue() / 100.0);
            finalAllocationValues.put(entry.getKey(), 0.0); // Initialize final allocation values
        }

        Map<String, Integer> finalStockQuantities = new HashMap<>();

        // Iterate over the current stocks and apply adjustments
        for (Map.Entry<String, PortfolioGroupingSummary.StockInfo> stockEntry : currentPortfolio.getPortfolioStocks()
                .entrySet()) {
            String stockSymbol = stockEntry.getKey();
            PortfolioGroupingSummary.StockInfo stockInfo = stockEntry.getValue();

            // Calculate final quantity
            int currentQuantity = stockInfo.getQuantity();
            double adjustment = stockAdjustments.getOrDefault(stockSymbol, 0.0);
            int finalQuantity = currentQuantity + (int) Math.round(adjustment);

            // Store in finalStockQuantities
            finalStockQuantities.put(stockSymbol, finalQuantity);
        }

        adjustments.put("finalStocks", finalStockQuantities);

        return adjustments;
    }

    @Override
    public Map<String, String> executeRebalancePortfolioTransactions(
            PortfolioStockRebalancingDTO portfolioStocksToBeAdjusted, Integer portfolioId) {
        Portfolio portfolio = portfolioService.findById(portfolioId)
                .orElseThrow(() -> new EntityNotFoundException("Portfolio not found"));
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : portfolioStocksToBeAdjusted.getPortfolioStocks().entrySet()) {
            if ("CASH".equals(entry.getKey())) {
                continue;
            }
            if (entry.getValue() < 0) {
                processStockTransaction(portfolio, entry.getKey(), entry.getValue());
            }
        }

        portfolioService.updatePortfolio(portfolio.getPortfolioId(), portfolio);

        // Process Buys
        for (Map.Entry<String, Integer> entry : portfolioStocksToBeAdjusted.getPortfolioStocks().entrySet()) {
            if ("CASH".equals(entry.getKey())) {
                continue;
            }
            if (entry.getValue() > 0) {
                processStockTransaction(portfolio, entry.getKey(), entry.getValue());
            }
        }

        portfolioService.updatePortfolio(portfolio.getPortfolioId(), portfolio);
        result.put("message", "Portfolio successfully rebalanced");
        return result;
    }

    private void processStockTransaction(Portfolio portfolio, String stockSymbol, int quantity) {
        PortfolioStock portfolioStock = portfolioStockRepository
                .findByPortfolioPortfolioIdAndStockStockSymbol(portfolio.getPortfolioId(), stockSymbol)
                .orElseThrow(() -> new EntityNotFoundException("Portfolio stock not found in the portfolio"));

        double stockPrice = marketDataService.fetchCurrentData(stockSymbol).path("Global Quote")
                .path("05. price").asDouble();

        double transactionAmount = stockPrice * quantity;

        if (quantity > 0 && portfolio.getRemainingCapital() < transactionAmount) {
            throw new IllegalArgumentException("Insufficient funds to purchase stock");
        }

        int newQuantity = portfolioStock.getQuantity() + quantity;
        if (newQuantity <= 0) {
            portfolio
                    .setRemainingCapital(portfolio.getRemainingCapital() + (stockPrice * portfolioStock.getQuantity()));
            portfolioStockRepository.delete(portfolioStock);
        } else {
            portfolioStock.setQuantity(newQuantity);
            portfolioStock.setBuyPrice((float) stockPrice);
            portfolioStock.setBuyDate(LocalDate.now());

            if (quantity < 0) {
                portfolio.setRemainingCapital(portfolio.getRemainingCapital() + (-transactionAmount));
            } else {
                portfolio.setRemainingCapital(portfolio.getRemainingCapital() - transactionAmount);
            }
            portfolioStockRepository.save(portfolioStock);
        }

        String action = String.format(
                "User successfully %s stock %s in Portfolio #%d - %s with new price: %.2f and quantity: %d on %s",
                (quantity < 0) ? "sold" : "bought",
                stockSymbol, portfolio.getPortfolioId(), portfolio.getName(), stockPrice, Math.abs(quantity),
                LocalDate.now());

        accessLogRepository.save(new AccessLog(portfolio.getUser(), action));
    }

}
