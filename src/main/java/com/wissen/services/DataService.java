package com.wissen.services;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException.NotFound;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wissen.dto.LeaderboardDTO;
import com.wissen.dto.LeaderboardModel;
import com.wissen.dto.QuestionsDTO;
import com.wissen.dto.QuestionsModel;

@Service
public class DataService {

	@Value("${cookie.jigar}")
	String jigarCookie;

	@Value("${cookie.anirudh}")
	String anirudhCookie;
	
	private static Logger LOG = LoggerFactory.getLogger(DataService.class);

	public CellStyle cellStyle(Workbook wb, IndexedColors colour) {
		CellStyle style = wb.createCellStyle();
		style.setFillForegroundColor(colour.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

		return style;
	}

	public String leadboardUrlFor(String questionUrl) {
		return "https://www.hackerrank.com/rest/contests/master/challenges/" + questionUrl
				+ "/leaderboard/filter?offset=0&limit=100&include_practice=true&friends=follows&filter_kinds=friends";
	}

	public String questionsUrlFor(String profile) {
		return "https://www.hackerrank.com/rest/hackers/" + profile
				+ "/recent_challenges?limit=1000&cursor=&response_version=v1";
	}

	public HttpHeaders setCookie(boolean trackingForGrads) {
		HttpHeaders headers = new HttpHeaders();

		if (trackingForGrads)
			headers.set("Cookie", "{" + jigarCookie + "}");
		else
			headers.set("Cookie", "{" + anirudhCookie + "}");

		return headers;
	}

	public void cellColour(int weekTotal, Cell cell, CellStyle green, CellStyle amber, CellStyle red) {
		if (weekTotal >= 6)
			cell.setCellStyle(green);
		else if (weekTotal > 3)
			cell.setCellStyle(amber);
		else
			cell.setCellStyle(red);
	}

	public String dataFor(InputStream fIP, String profileFilename) {
		RestTemplate restTemplate = new RestTemplate();
		ObjectMapper mapper = new ObjectMapper();

		XSSFWorkbook profileWorkbook = null;
		try {
			profileWorkbook = new XSSFWorkbook(fIP);
		} catch (IOException e) {
			LOG.error(e.getMessage());
			throw new RuntimeException();
		}

		boolean trackingForGrads = false;
		if (profileFilename.contains("grads"))
			trackingForGrads = true;

		Sheet profileSheet = profileWorkbook.getSheetAt(0);

		Workbook leaderboardWorkbook = new XSSFWorkbook();
		Sheet leaderboardSheet = leaderboardWorkbook.createSheet();

		Row headerRow = leaderboardSheet.createRow(0);
		Cell headerCells = headerRow.createCell(0);
		headerCells.setCellValue("Name");

		CellStyle red = cellStyle(leaderboardWorkbook, IndexedColors.RED);
		CellStyle amber = cellStyle(leaderboardWorkbook, IndexedColors.YELLOW);
		CellStyle green = cellStyle(leaderboardWorkbook, IndexedColors.GREEN);

		for (int i = 1; i <= 50; i++)
			headerRow.createCell(i);

		Set<String> allQuestions = new HashSet<>();

		// <profile,Name>
		Map<String, String> profileToName = new HashMap<>();

		// <profile, <Week, Solved>>
		Map<String, Map<LocalDate, Integer>> profileToCount = new HashMap<>();

		// <profile,college>
		Map<String, String> profileToCollegeName = new HashMap<>();

		int rowNum = 1;
		int maxProfiles = profileSheet.getPhysicalNumberOfRows();
		for (Row row : profileSheet) {
			if (rowNum++ > maxProfiles)
				break;

			try {
				boolean isEliminated = (int) row.getCell(2).getNumericCellValue() == 0 ? false : true;

				if (!isEliminated) {
					String name = row.getCell(0).getStringCellValue();
					String profile = row.getCell(1).getStringCellValue();
					LOG.info(name + " -> " + profile);
					profileToName.put(profile.toLowerCase(), name);
					profileToCount.put(profile.toLowerCase(), new HashMap<>());
					if (trackingForGrads) {
						String collegeName = row.getCell(3).getStringCellValue();
						profileToCollegeName.put(profile.toLowerCase(), collegeName);
					}
				}

			} catch (NullPointerException e) {
				break;
			}
		}

		LOG.info("\n\n\n\n");

		rowNum = 1;
		// for (Row row : profileSheet) {
		for (String profile : profileToName.keySet()) {
			if (rowNum++ > maxProfiles)
				break;

			// String profile = row.getCell(1).getStringCellValue();

			String url = questionsUrlFor(profile);
			LOG.info("url is: " + url);
			ResponseEntity<String> response = null;
			boolean infiniteLoop = true;
			while (infiniteLoop) {
				try {
					response = restTemplate.getForEntity(url, String.class);
					infiniteLoop = false;
				} catch (NotFound e) {
					LOG.info("Username changed : " + profile);
					infiniteLoop = false;
				} catch (RestClientException e) {

					LOG.info(e.getMessage());
				}
			}

			QuestionsDTO resp = null;
			try {
				resp = mapper.readValue(response.getBody(), new TypeReference<QuestionsDTO>() {
				});
			} catch (IOException e) {
				LOG.error(e.getMessage());
				throw new RuntimeException();
			}

			List<QuestionsModel> submissions = resp.getModels();
			for (QuestionsModel currSubmission : submissions) {
				if (!allQuestions.contains(currSubmission.getCh_slug()))
					allQuestions.add(currSubmission.getCh_slug());
			}

			try {
				Thread.sleep((long) (Math.random() * 250));
			} catch (InterruptedException e) {
				LOG.warn("Thread interupted..."+e.getMessage());
			}
		}

		LOG.info("\n\n\n\n");
		LOG.info("Starting the partaaay");
		rowNum = 1;
		// allQuestions.forEach(questionUrl -> {
		HttpHeaders headers = setCookie(trackingForGrads);

		for (String questionUrl : allQuestions) {
			String leaderBoardUrl = leadboardUrlFor(questionUrl);

			// if(rowNum++ > 20)
			// break;

			HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
			boolean infiniteLoop = true;
			ResponseEntity<LeaderboardDTO> respEntity = null;

			while (infiniteLoop) {

				try {
					respEntity = restTemplate.exchange(leaderBoardUrl, HttpMethod.GET, entity, LeaderboardDTO.class);

					infiniteLoop = false;

				} catch (RestClientException e) {
					LOG.info(e.getMessage());
				}
			}

			LOG.info("==============" + questionUrl + "================ " + rowNum++);

			List<LeaderboardModel> friendsLeaderboard = respEntity.getBody().getModels();
			friendsLeaderboard.forEach(curr -> {
				String currProfile = curr.getHacker().toLowerCase();
				// LOG.info(currProfile + " Current profile");
				Map<LocalDate, Integer> dateToCount = profileToCount.getOrDefault(currProfile, new HashMap<>());

				if (curr.getRank().equals("1")) {

					LocalDate date = Instant.ofEpochSecond(curr.getTime_taken())
							.atZone(TimeZone.getDefault().toZoneId()).toLocalDate();
					// if(date.compareTo(LocalDate.parse("2019-01-01")) < 0)
					// LOG.info(date);
					dateToCount.putIfAbsent(date, 0);
					dateToCount.put(date, dateToCount.get(date) + 1);
					// LOG.info(currProfile + " " + questionUrl + " TEMP: " + temp);
				}
			});
			try {
				Thread.sleep((long) (Math.random() * 100));
			} catch (InterruptedException e) {
				LOG.warn("Thread interupted..."+e.getMessage());
			}
		}

		LocalDate trackingStartDate = LocalDate.parse("2019-08-16");
		LocalDate today = LocalDate.now();

		List<SimpleEntry<String, Integer>> ranklist = profileToCount.entrySet().stream().flatMap(a -> {

			int count = a.getValue().entrySet().stream().filter(countPerDate -> {
				return countPerDate.getKey().compareTo(trackingStartDate) >= 0
						&& countPerDate.getKey().compareTo(today) < 0;
			}).flatMapToInt(countPerDate -> IntStream.of(countPerDate.getValue())).sum();

			SimpleEntry<String, Integer> entryCount = new SimpleEntry<>(a.getKey(), count);
			return Stream.of(entryCount);

		}).sorted((u, v) -> v.getValue().compareTo(u.getValue())).collect(Collectors.toList());

		rowNum = 1;
		int maxNumNameCharacters = 0;
		int maxNumCollegeNameCharacters = 0;

		int offset = 1;

		if (trackingForGrads)
			offset = 2;

		// for (Entry<String, Map<LocalDate, Integer>> currProfile :
		// profileToCount.entrySet()) {
		for (Entry<String, Integer> hacker : ranklist) {

			String hackerProfile = hacker.getKey();

			Row boardRow = leaderboardSheet.createRow(rowNum++);
			String name = profileToName.getOrDefault(hackerProfile, hackerProfile.toLowerCase());
			maxNumNameCharacters = Math.max(maxNumNameCharacters, name.length());

			String collegeName = "";
			if (trackingForGrads) {
				collegeName = profileToCollegeName.getOrDefault(hackerProfile, "Hacking University");
				maxNumCollegeNameCharacters = Math.max(maxNumCollegeNameCharacters, collegeName.length());
			}

			boardRow.createCell(0).setCellValue(name);

			if (trackingForGrads) {
				headerRow.getCell(1).setCellValue("College Name");
				boardRow.createCell(1).setCellValue(collegeName);
			}

			// keeping the start of first week same for both
			// first week is till 25 Aug, 19[Sun]
			LocalDate startDate = trackingStartDate;
			LocalDate currWeekStart = startDate;

			LocalDate firstWeekEnd = null;

			if (trackingForGrads)
				// first week end for grads was this (club started later for them)
				firstWeekEnd = LocalDate.parse("2019-09-09");
			else
				firstWeekEnd = LocalDate.parse("2019-08-26");

			int totalWeeksUntillNow = (int) Math.ceil(ChronoUnit.DAYS.between(firstWeekEnd, today) / 7.0);
			totalWeeksUntillNow++;

			int total = 0;
			int currWeek = 1;

			Map<LocalDate, Integer> solvedPerDay = profileToCount.get(hackerProfile);

			int weekIdx = offset + totalWeeksUntillNow - currWeek + 1;

			while (currWeekStart.compareTo(firstWeekEnd) < 0) {
				int weekTotal = 0;
				LocalDate currDate = currWeekStart;
				LocalDate currWeekEnd = firstWeekEnd;
				while (currDate.compareTo(currWeekEnd) < 0) {
					weekTotal += solvedPerDay.getOrDefault(currDate, 0);
					currDate = currDate.plusDays(1);
				}

				total += weekTotal;

				headerRow.getCell(weekIdx).setCellValue(currWeek);
				boardRow.createCell(weekIdx).setCellValue(weekTotal);

				Cell cell = boardRow.getCell(weekIdx);
				cellColour(weekTotal, cell, green, amber, red);

				currWeekStart = currWeekEnd;
			}

			startDate = firstWeekEnd;

			currWeekStart = startDate;
			currWeek = 2;
			while (currWeekStart.compareTo(today) < 0) {
				weekIdx = offset + totalWeeksUntillNow - currWeek + 1;

				int weekTotal = 0;
				LocalDate currDate = currWeekStart;
				LocalDate currWeekEnd = currWeekStart.plusDays(7);
				while (currDate.compareTo(currWeekEnd) < 0 && currDate.compareTo(today) < 0) {
					weekTotal += solvedPerDay.getOrDefault(currDate, 0);
					currDate = currDate.plusDays(1);
				}

				total += weekTotal;

				headerRow.getCell(weekIdx).setCellValue(currWeek);
				boardRow.createCell(weekIdx).setCellValue(weekTotal);

				Cell cell = boardRow.getCell(weekIdx);
				cellColour(weekTotal, cell, green, amber, red);

				currWeek++;
				currWeekStart = currWeekEnd;
			}

			headerRow.getCell(offset).setCellValue("Total");
			boardRow.createCell(offset).setCellValue(total);

			LOG.info("Calc done for: " + hackerProfile);
		}

		// so column doesn't get squeezed, this way is better than using
		// autoSizeColumn()
		int width = ((int) (maxNumNameCharacters * 1.14388)) * 256;
		leaderboardSheet.setColumnWidth(0, width);

		if (trackingForGrads) {
			width = ((int) (maxNumCollegeNameCharacters * 1.14388)) * 256;
			leaderboardSheet.setColumnWidth(1, width);
		}

		Font boldFont = leaderboardWorkbook.createFont();
		boldFont.setBold(true);
		CellStyle boldStyle = leaderboardWorkbook.createCellStyle();
		boldStyle.setFont(boldFont);
		headerRow.setRowStyle(boldStyle);

		FileOutputStream opFile = null;
		try {

			String baseDir = System.getenv("BASE_DIR");
			if (baseDir == null)
				baseDir = "";
			else
				baseDir += "/";

			String filePath = baseDir + "leaderboard_" + profileFilename;

			opFile = new FileOutputStream(filePath);

			leaderboardWorkbook.write(opFile);
			opFile.close();
			leaderboardWorkbook.close();
		} catch (IOException e) {
			LOG.error(e.getMessage());
			throw new RuntimeException();
		}

		return "done";
	}

}
