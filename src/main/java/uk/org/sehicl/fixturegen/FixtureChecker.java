package uk.org.sehicl.fixturegen;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import uk.org.sehicl.website.data.League;
import uk.org.sehicl.website.data.Match;
import uk.org.sehicl.website.data.Model;

public class FixtureChecker
{
    public static void main(String[] args) throws Exception
    {
        Model model;
        try (FileReader r = new FileReader("2025-26.xml"))
        {
            final XmlMapper mapper = new XmlMapper();
            mapper.setTimeZone(TimeZone.getDefault());
            model = mapper.readValue(r, Model.class);
        }
        checkForDuplicateCourts(
                model.getLeagues().stream().map(League::getMatches).flatMap(Collection::stream));
        fixturesByTeam(model).forEach(FixtureChecker::checkFixtureList);
        System.out.println("All checks run, no problems found");
    }

    private static void checkForDuplicateCourts(Stream<Match> matches)
    {
        Set<String> dateTimeCourtStrings = new HashSet<>();
        matches
                .map(m -> String
                        .format("%s/%s",
                                DateFormatUtils.ISO_8601_EXTENDED_DATETIME_FORMAT
                                        .format(m.getDateTime()),
                                m.getCourt()))
                .forEach(str ->
                {
                    if (!dateTimeCourtStrings.add(str))
                        throw new RuntimeException(
                                String.format("Duplicate matches on date/court %s", str));
                });
        ;
    }

    private static Map<String, List<Match>> fixturesByTeam(Model model)
    {
        Map<String, List<Match>> answer = new HashMap<>();
        model.getLeagues().stream().flatMap(l -> l.getMatches().stream()).forEach(match ->
        {
            Stream
                    .of(match.getHomeTeamId(), match.getAwayTeamId())
                    .forEach(team -> answer
                            .computeIfAbsent(team, key -> new ArrayList<>())
                            .add(match));
        });
        return answer;
    }

    private static void checkFixtureList(String team, List<Match> fixtures)
    {
        checkForSameDayFixtures(team, fixtures);
        checkForTooManyNineOClocks(team, fixtures);
        checkEveryonePlaysEveryoneTheSameNumberOfTimes(team, fixtures);
        checkHomeAwayBalance(team, fixtures);
        checkFixtureGaps(team, fixtures);
    }

    static void checkForSameDayFixtures(String team, List<Match> fixtures)
    {
        Set<String> dates = new HashSet<>();
        fixtures
                .stream()
                .map(Match::getDateTime)
                .map(DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT::format)
                .forEach(d ->
                {
                    if (!dates.add(d))
                        throw new RuntimeException(
                                String.format("Multiple games for %s on %s", team, d));
                });
    }

    static void checkForTooManyNineOClocks(String team, List<Match> fixtures)
    {
        int maxCount = team.equals("Parco") ? 2 : 1;
        if (fixtures
                .stream()
                .map(Match::getDateTime)
                .map(DateFormatUtils.ISO_8601_EXTENDED_TIME_FORMAT::format)
                .filter("21:00:00"::equals)
                .count() > maxCount)
        {
            throw new RuntimeException(
                    String.format("More than %d 9 o'clock games for %s", maxCount, team));
        }
        ;
    }

    static void checkEveryonePlaysEveryoneTheSameNumberOfTimes(String team, List<Match> fixtures)
    {
        Map<String, List<Match>> matchesByOpponent = fixtures
                .stream()
                .collect(Collectors
                        .groupingBy(
                                match -> match.getHomeTeamId().equals(team) ? match.getAwayTeamId()
                                        : match.getHomeTeamId()));
        AtomicInteger lastCount = new AtomicInteger();
        matchesByOpponent.forEach((opp, matches) ->
        {
            int count = matches.size();
            if (!lastCount.compareAndSet(0, count))
                if (lastCount.get() != count)
                    throw new RuntimeException(String
                            .format("%s and %s play %d game(s) against each other but others play %d",
                                    team, opp, count, lastCount.get()));
        });
    }

    static void checkHomeAwayBalance(String team, List<Match> fixtures)
    {
        var haCounts = fixtures
                .stream()
                .collect(Collectors
                        .groupingBy(match -> match.getHomeTeamId().equals(team) ? "H" : "A"))
                .values()
                .stream()
                .mapToInt(Collection::size)
                .toArray();
        if (haCounts.length == 1 && haCounts[0] != 1)
            throw new RuntimeException(
                    "%s: Unbalanced home and away counts (%d and 0)".formatted(team, haCounts[0]));
        if (Math.abs(haCounts[0] - haCounts[1]) > 1)
            throw new RuntimeException("%s: Unbalanced home and away counts (%d and %d)"
                    .formatted(team, haCounts[0], haCounts[1]));
    }

    static void checkFixtureGaps(String team, List<Match> fixtures)
    {
        var dates = fixtures
                .stream()
                .map(Match::getDateTime)
                .map(Date::getTime)
                .map(TimeUnit.MILLISECONDS::toDays)
                .sorted()
                .mapToInt(Long::intValue)
                .toArray();
        var diffs = IntStream
                .range(1, dates.length)
                .map(i -> (dates[i] - dates[i - 1]) / 7)
                .mapToObj("%d"::formatted)
                .collect(Collectors.joining());
        if (diffs.contains("11"))
            throw new RuntimeException(
                    "%s: More than two successive games (%s)".formatted(team, diffs));
    }
}
