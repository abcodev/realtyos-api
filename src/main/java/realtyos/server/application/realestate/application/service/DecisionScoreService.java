package realtyos.server.application.realestate.application.service;

import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class DecisionScoreService {

    public DecisionCandidate score(DecisionCandidate candidate, RagSearchCondition condition) {
        double budgetScore = budgetScore(candidate, condition);
        double areaScore = areaScore(candidate, condition);
        double liquidityScore = liquidityScore(candidate);
        double recencyScore = recencyScore(candidate);
        double score = budgetScore * 0.35
                + areaScore * 0.25
                + liquidityScore * 0.20
                + recencyScore * 0.20;

        List<String> strengths = new ArrayList<>();
        List<String> cautions = new ArrayList<>();

        if (budgetScore >= 85) {
            strengths.add("예산 조건에 잘 맞습니다.");
        } else if (budgetScore < 55) {
            cautions.add("예산 상한에 가깝거나 일부 거래가 예산을 초과합니다.");
        }
        if (areaScore >= 85) {
            strengths.add("요청한 면적 조건과 거래 면적이 잘 맞습니다.");
        } else if (areaScore < 55) {
            cautions.add("요청한 면적 조건과 일부 거래 면적 차이가 있습니다.");
        }
        if (liquidityScore >= 70) {
            strengths.add("최근 거래 건수가 있어 가격 판단 근거가 비교적 충분합니다.");
        } else {
            cautions.add("최근 거래 건수가 적어 가격 판단의 불확실성이 있습니다.");
        }
        if (recencyScore >= 80) {
            strengths.add("최근 거래가 확인됩니다.");
        } else {
            cautions.add("최근성이 낮아 현재 시세와 차이가 있을 수 있습니다.");
        }
        if (strengths.isEmpty()) {
            strengths.add("요청 조건에 일부 부합하는 실거래 근거가 있습니다.");
        }

        return new DecisionCandidate(
                candidate.apartmentName(),
                candidate.regionCode(),
                candidate.dongName(),
                candidate.latestDealDate(),
                candidate.latestDealAmount(),
                candidate.minDealAmount(),
                candidate.maxDealAmount(),
                candidate.averageDealAmount(),
                candidate.minExclusiveArea(),
                candidate.maxExclusiveArea(),
                candidate.averageExclusiveArea(),
                candidate.dealCount(),
                candidate.averagePricePerPyeong(),
                Math.round(score * 10.0) / 10.0,
                strengths,
                cautions,
                candidate.samples()
        );
    }

    private double budgetScore(DecisionCandidate candidate, RagSearchCondition condition) {
        if (condition == null || condition.maxPrice() == null || candidate.averageDealAmount() == null) {
            return 75;
        }
        if (candidate.averageDealAmount() <= condition.maxPrice()) {
            double room = (condition.maxPrice() - candidate.averageDealAmount()) / (double) condition.maxPrice();
            return clamp(80 + room * 20);
        }
        double over = (candidate.averageDealAmount() - condition.maxPrice()) / (double) condition.maxPrice();
        return clamp(70 - over * 100);
    }

    private double areaScore(DecisionCandidate candidate, RagSearchCondition condition) {
        if (condition == null || (condition.minArea() == null && condition.maxArea() == null)
                || candidate.averageExclusiveArea() == null) {
            return 75;
        }
        double area = candidate.averageExclusiveArea();
        if (condition.minArea() != null && area < condition.minArea()) {
            return clamp(75 - (condition.minArea() - area) * 2);
        }
        if (condition.maxArea() != null && area > condition.maxArea()) {
            return clamp(75 - (area - condition.maxArea()) * 2);
        }
        return 95;
    }

    private double liquidityScore(DecisionCandidate candidate) {
        long count = candidate.dealCount() == null ? 0 : candidate.dealCount();
        return clamp(Math.min(100, 35 + count * 13));
    }

    private double recencyScore(DecisionCandidate candidate) {
        if (candidate.latestDealDate() == null || candidate.latestDealDate().isBlank()) {
            return 40;
        }
        try {
            long days = ChronoUnit.DAYS.between(LocalDate.parse(candidate.latestDealDate()), LocalDate.now());
            return clamp(100 - Math.max(0, days) / 3.65);
        } catch (Exception e) {
            return 50;
        }
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
