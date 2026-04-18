-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Mar 31, 2026 at 02:00 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `rfid verification`
--

-- --------------------------------------------------------

--
-- Table structure for table `students_information`
--

CREATE TABLE `students_information` (
  `rfid_number` varchar(255) NOT NULL,
  `firstname` varchar(255) NOT NULL,
  `lastname` varchar(255) NOT NULL,
  `section` varchar(255) NOT NULL,
  `time_in` datetime DEFAULT NULL,
  `time_out` datetime DEFAULT NULL,
  `day` date DEFAULT NULL,
  `parent_email` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `students_information`
--

INSERT INTO `students_information` (`rfid_number`, `firstname`, `lastname`, `section`, `time_in`, `time_out`, `day`, `parent_email`) VALUES
('1234567890', 'RJ', 'Inovejas', 'ICT 12 - D', NULL, NULL, NULL, ''),
('6003848', 'Lanz Jasper', 'Luat', 'ICT 12 - D', NULL, NULL, NULL, ''),
('6010863', 'Kate', 'Perez', 'ICT 12 - E', NULL, NULL, NULL, ''),
('6054398', 'Jean Louise', 'Pineda', 'ICT 12 - D', NULL, NULL, NULL, ''),
('6060265', 'Jaezel', 'Sarmiento', 'ICT 12 - D', NULL, NULL, NULL, ''),
('6070669', 'Grace', 'Dumolon', 'ICT 12 - D', NULL, NULL, NULL, '');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `students_information`
--
ALTER TABLE `students_information`
  ADD PRIMARY KEY (`rfid_number`),
  ADD KEY `rfid_number` (`rfid_number`) USING BTREE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
