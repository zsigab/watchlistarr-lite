package watchlistarr.config;

public record DeleteConfig(
        boolean movieDeleting,
        boolean endedShowDeleting,
        boolean continuingShowDeleting,
        long deleteIntervalDays,
        boolean deleteFiles
) {
}
