package realtyos.server.application.realestate.application.service;

import org.springframework.stereotype.Component;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionResult;
import realtyos.server.application.realestate.domain.DecisionTargetSummary;

import java.util.List;

@Component
public class DecisionResultFormatter {

    public String format(DecisionResult result) {
        if (result.candidates().isEmpty()) {
            return result.summary();
        }
        if (result.comparisonTargets().size() > 1) {
            return formatComparisonAnswer(result);
        }

        StringBuilder answer = new StringBuilder();
        answer.append(result.summary()).append("\n\n");
        appendSummaryTable(answer, result.targetSummaries());
        answer.append("| 순위 | 후보 | 점수 | 최근 거래 | 평균가 | 평당가 | 거래건수 |\n");
        answer.append("|---:|---|---:|---|---:|---:|---:|\n");
        for (int i = 0; i < Math.min(5, result.candidates().size()); i++) {
            DecisionCandidate candidate = result.candidates().get(i);
            answer.append("| ")
                    .append(i + 1)
                    .append(" | ")
                    .append(candidate.dongName()).append(" ").append(candidate.apartmentName())
                    .append(" | ")
                    .append(candidate.score())
                    .append(" | ")
                    .append(safe(candidate.latestDealDate())).append(" / ").append(formatPrice(candidate.latestDealAmount()))
                    .append(" | ")
                    .append(formatPrice(candidate.averageDealAmount()))
                    .append(" | ")
                    .append(formatPrice(candidate.averagePricePerPyeong()))
                    .append(" | ")
                    .append(candidate.dealCount() == null ? 0 : candidate.dealCount())
                    .append(" |")
                    .append("\n");
        }

        answer.append("\n판단 포인트:\n");
        result.candidates().stream().limit(3).forEach(candidate -> {
            answer.append("- ")
                    .append(candidate.dongName()).append(" ").append(candidate.apartmentName())
                    .append(": ")
                    .append(String.join(" ", candidate.strengths()));
            if (!candidate.cautions().isEmpty()) {
                answer.append(" 주의: ").append(String.join(" ", candidate.cautions()));
            }
            answer.append("\n");
        });

        answer.append("\n근거 거래:\n");
        result.candidates().stream().limit(3)
                .flatMap(candidate -> candidate.samples().stream().limit(2))
                .forEach(sample -> answer.append("- ").append(formatSample(sample)).append("\n"));
        answer.append("\n위 결과는 최근 실거래 데이터의 예산 적합도, 면적 적합도, 거래건수, 최근성을 함께 점수화한 의사결정 보조 결과입니다.");
        return answer.toString();
    }

    private String formatComparisonAnswer(DecisionResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append(result.summary()).append("\n\n");
        answer.append("| 비교대상 | 대표 후보 | 최고점 | 최근가 | 평균가 | 중위가 | 평당가 | 거래건수 | 3개월 변화 |\n");
        answer.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");

        for (String target : result.comparisonTargets()) {
            List<DecisionCandidate> targetCandidates = candidatesForTarget(result.candidates(), target);
            DecisionTargetSummary summary = summaryForTarget(result.targetSummaries(), target);
            if (targetCandidates.isEmpty()) {
                answer.append("| ").append(target).append(" | 근거 없음 | - | - | - | - | - | 0 | - |\n");
                continue;
            }
            DecisionCandidate best = targetCandidates.getFirst();
            answer.append("| ")
                    .append(target)
                    .append(" | ")
                    .append(best.dongName()).append(" ").append(best.apartmentName())
                    .append(" | ")
                    .append(best.score())
                    .append(" | ")
                    .append(formatPrice(summary == null ? best.latestDealAmount() : summary.latestDealAmount()))
                    .append(" | ")
                    .append(formatPrice(best.averageDealAmount()))
                    .append(" | ")
                    .append(formatPrice(summary == null ? null : summary.medianDealAmount()))
                    .append(" | ")
                    .append(formatPrice(best.averagePricePerPyeong()))
                    .append(" | ")
                    .append(summary == null ? best.dealCount() == null ? 0 : best.dealCount() : summary.dealCount())
                    .append(" | ")
                    .append(formatPercent(summary == null ? null : summary.threeMonthChangeRate()))
                    .append(" |\n");
        }

        answer.append("\n대상별 후보:\n");
        for (String target : result.comparisonTargets()) {
            List<DecisionCandidate> targetCandidates = candidatesForTarget(result.candidates(), target);
            answer.append("\n").append(target).append("\n");
            if (targetCandidates.isEmpty()) {
                answer.append("- 조건에 맞는 최근 실거래 후보를 찾지 못했습니다.\n");
                continue;
            }
            targetCandidates.stream().limit(3).forEach(candidate -> answer.append("- ")
                    .append(candidate.dongName()).append(" ").append(candidate.apartmentName())
                    .append(": 점수 ").append(candidate.score())
                    .append(", 최근 ").append(safe(candidate.latestDealDate()))
                    .append(" / ").append(formatPrice(candidate.latestDealAmount()))
                    .append(", 평균 ").append(formatPrice(candidate.averageDealAmount()))
                    .append(", 거래 ").append(candidate.dealCount() == null ? 0 : candidate.dealCount()).append("건")
                    .append("\n"));
        }

        answer.append("\n판단 포인트:\n");
        for (String target : result.comparisonTargets()) {
            List<DecisionCandidate> targetCandidates = candidatesForTarget(result.candidates(), target);
            if (targetCandidates.isEmpty()) {
                continue;
            }
            DecisionCandidate best = targetCandidates.getFirst();
            answer.append("- ").append(target).append(": ")
                    .append(best.dongName()).append(" ").append(best.apartmentName())
                    .append(" 기준으로 ")
                    .append(String.join(" ", best.strengths()));
            if (!best.cautions().isEmpty()) {
                answer.append(" 주의: ").append(String.join(" ", best.cautions()));
            }
            answer.append("\n");
        }

        answer.append("\n근거 거래:\n");
        for (String target : result.comparisonTargets()) {
            candidatesForTarget(result.candidates(), target).stream().limit(2)
                    .flatMap(candidate -> candidate.samples().stream().limit(1))
                    .forEach(sample -> answer.append("- ").append(target).append(" / ").append(formatSample(sample)).append("\n"));
        }
        answer.append("\n위 비교는 최근 실거래 데이터의 예산 적합도, 면적 적합도, 거래건수, 최근성을 함께 점수화한 의사결정 보조 결과입니다.");
        return answer.toString();
    }

    private List<DecisionCandidate> candidatesForTarget(List<DecisionCandidate> candidates, String target) {
        return candidates.stream()
                .filter(candidate -> {
                    String dongName = candidate.dongName() == null ? "" : candidate.dongName();
                    String apartmentName = candidate.apartmentName() == null ? "" : candidate.apartmentName();
                    return dongName.contains(target)
                            || target.contains(dongName)
                            || apartmentName.contains(target)
                            || target.contains(apartmentName);
                })
                .toList();
    }

    private void appendSummaryTable(StringBuilder answer, List<DecisionTargetSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        answer.append("| 구분 | 최근가 | 평균가 | 중위가 | 최저/최고 | 평당가 | 거래건수 | 3개월 변화 |\n");
        answer.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (DecisionTargetSummary summary : summaries) {
            answer.append("| ")
                    .append(summary.name())
                    .append(" | ")
                    .append(formatPrice(summary.latestDealAmount()))
                    .append(" | ")
                    .append(formatPrice(summary.averageDealAmount()))
                    .append(" | ")
                    .append(formatPrice(summary.medianDealAmount()))
                    .append(" | ")
                    .append(formatPrice(summary.minDealAmount())).append("~").append(formatPrice(summary.maxDealAmount()))
                    .append(" | ")
                    .append(formatPrice(summary.averagePricePerPyeong()))
                    .append(" | ")
                    .append(summary.dealCount() == null ? 0 : summary.dealCount())
                    .append(" | ")
                    .append(formatPercent(summary.threeMonthChangeRate()))
                    .append(" |\n");
        }
        answer.append("\n");
    }

    private DecisionTargetSummary summaryForTarget(List<DecisionTargetSummary> summaries, String target) {
        if (summaries == null) {
            return null;
        }
        return summaries.stream()
                .filter(summary -> summary.name().equals(target))
                .findFirst()
                .orElse(null);
    }

    private String formatSample(DecisionDealSample sample) {
        return "%s %s / %s / %s / %s㎡"
                .formatted(sample.dongName(), sample.apartmentName(), sample.dealDate(),
                        formatPrice(sample.dealAmount()), sample.exclusiveArea());
    }

    private String formatPrice(Long price) {
        if (price == null) {
            return "정보없음";
        }
        long eok = price / 10000;
        long manwon = price % 10000;
        if (eok > 0 && manwon > 0) {
            return eok + "억 " + manwon + "만원";
        }
        if (eok > 0) {
            return eok + "억원";
        }
        return price + "만원";
    }

    private String formatPercent(Double value) {
        if (value == null) {
            return "정보없음";
        }
        return "%.1f%%".formatted(value);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "정보없음" : value;
    }
}
