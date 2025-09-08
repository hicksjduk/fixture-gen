package uk.org.sehicl.fixturegen;

import java.io.FileWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import uk.org.sehicl.website.data.League;
import uk.org.sehicl.website.data.Match;
import uk.org.sehicl.website.data.Model;
import uk.org.sehicl.website.data.Team;
import uk.org.sehicl.website.data.TeamReference;

public class FixtureGenerator
{
    public static void main(String[] args) throws Exception
    {
        final Model model = model();
        try (FileWriter w = new FileWriter("2025-26.xml"))
        {
            final XmlMapper mapper = new XmlMapper();
            mapper.setTimeZone(TimeZone.getDefault());
            mapper.writeValue(w, model);
        }
    }

    public static Model model()
    {
        return FixtureData.modelBuilder().build();
    }

    private static final Set<String> identifiers = new HashSet<>();

    public static String generateId(String name)
    {
        String idStem = name.replaceAll("[^A-Za-z0-9]", "");
        String answer = idStem;
        int index = 0;
        while (identifiers.contains(answer))
        {
            answer = idStem + (index++);
        }
        identifiers.add(answer);
        return answer;
    }

    private static final DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yy HH:mm");

    private static Fixture fixture(String dateTime, String court, int homeTeam, int awayTeam)
    {
        try
        {
            return new Fixture(dateFormatter.parse(dateTime), court, homeTeam, awayTeam);
        }
        catch (ParseException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public static record Fixture(Date dateTime, String court, int homeTeam, int awayTeam)
    {
    }

    public static class FixtureListBuilder
    {
        private final boolean indices1Based;
        private final List<Fixture> fixtures = new ArrayList<>();

        public FixtureListBuilder(boolean indices1Based)
        {
            this.indices1Based = indices1Based;
        }

        public FixtureListBuilder add(String dateTime, String court, int homeTeam, int awayTeam)
        {
            fixtures
                    .add(fixture(dateTime, court, homeTeam - (indices1Based ? 1 : 0),
                            awayTeam - (indices1Based ? 1 : 0)));
            return this;
        }

        public List<Fixture> build()
        {
            return fixtures;
        }
    }

    public static class TeamListBuilder
    {
        private class TeamData
        {
            private final Team team;
            private final Set<Integer> indicesToAvoid;

            public TeamData(Team team, Set<Integer> indicesToAvoid)
            {
                this.team = team;
                this.indicesToAvoid = indicesToAvoid;
            }

            @Override
            public String toString()
            {
                return "TeamData [team=" + team.getName() + ", indicesToAvoid=" + indicesToAvoid
                        + "]";
            }
        }

        private final boolean indices1Based;
        private final Set<TeamData> teamData = new HashSet<>();

        public TeamListBuilder(boolean indices1Based)
        {
            this.indices1Based = indices1Based;
        }

        public TeamListBuilder add(String name, Integer... indicesToAvoid)
        {
            Team team = new Team();
            team.setName(name);
            team.setId(generateId(name));
            teamData
                    .add(new TeamData(team,
                            Arrays
                                    .asList(indicesToAvoid)
                                    .stream()
                                    .map(i -> i - (indices1Based ? 1 : 0))
                                    .collect(Collectors.toSet())));
            return this;
        }

        public List<Team> build()
        {
            var teamList = new Team[teamData.size()];
            var rand = new Random();
            teamData
                    .stream()
                    .sorted(Comparator
                            .comparing(td -> td.indicesToAvoid,
                                    Comparator.comparing(Set::size, Comparator.reverseOrder())))
                    .forEach(td ->
                    {
                        var validIndices = IntStream
                                .range(0, teamList.length)
                                .filter(n -> !td.indicesToAvoid.contains(n))
                                .filter(n -> teamList[n] == null)
                                .toArray();
                        if (validIndices.length == 0)
                            throw new RuntimeException("Unable to determine index for team: " + td);
                        var i = validIndices[rand.nextInt(validIndices.length)];
                        teamList[i] = td.team;
                    });
            var answer = Stream.of(teamList).collect(Collectors.toList());
            System.out.println(answer.stream().map(Team::getName).collect(Collectors.toList()));
            return answer;
        }
    }

    public static class ModelBuilder
    {
        private final List<League> leagues = new ArrayList<>();

        public ModelBuilder add(String name, List<Team> teams, List<Fixture> fixtures)
        {
            League league = new League();
            league.setName(name);
            league.setId(generateId(name));
            teams.stream().forEach(league::addTeam);
            fixtures.forEach(f -> league.addMatch(generateMatch(f, teams)));
            leagues.add(league);
            return this;
        }

        private Match generateMatch(Fixture fixture, List<Team> teams)
        {
            Match answer = new Match();
            answer.setDateTime(fixture.dateTime());
            answer.setCourt(fixture.court());
            answer.setHomeTeam(getRef(teams.get(fixture.homeTeam())));
            answer.setAwayTeam(getRef(teams.get(fixture.awayTeam())));
            return answer;
        }

        private TeamReference getRef(Team team)
        {
            TeamReference answer = new TeamReference();
            answer.setId(team.getId());
            return answer;
        }

        public Model build()
        {
            Model answer = new Model();
            leagues.stream().forEach(answer::addLeague);
            return answer;
        }
    }
}
