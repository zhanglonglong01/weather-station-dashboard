CREATE TABLE station (
  code VARCHAR(32) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  location VARCHAR(200) NOT NULL,
  description VARCHAR(500) NOT NULL
);
CREATE TABLE observation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  station_code VARCHAR(32) NOT NULL,
  observed_at DATETIME NOT NULL,
  temperature DECIMAL(6,2),
  humidity DECIMAL(5,2),
  solar_radiation DECIMAL(9,2),
  outdoor_temperature DECIMAL(6,2),
  wind_speed DECIMAL(5,2),
  source VARCHAR(32) NOT NULL,
  CONSTRAINT fk_observation_station FOREIGN KEY (station_code) REFERENCES station(code),
  CONSTRAINT uq_station_time UNIQUE (station_code, observed_at),
  CONSTRAINT ck_temperature CHECK (temperature BETWEEN -60 AND 80),
  CONSTRAINT ck_humidity CHECK (humidity BETWEEN 0 AND 100),
  CONSTRAINT ck_light CHECK (solar_radiation BETWEEN 0 AND 2000),
  CONSTRAINT ck_outdoor_temperature CHECK (outdoor_temperature BETWEEN -60 AND 80),
  CONSTRAINT ck_wind_speed CHECK (wind_speed BETWEEN 0 AND 300),
  CONSTRAINT ck_some_measurement CHECK (temperature IS NOT NULL OR humidity IS NOT NULL OR solar_radiation IS NOT NULL OR outdoor_temperature IS NOT NULL OR wind_speed IS NOT NULL)
);
-- uq_station_time covers WHERE station_code = ? AND observed_at >= ? AND observed_at < ?.
INSERT INTO station(code, name, location, description) VALUES
('GH-001', '一号温室', '示范园区 · A区', '环境监测示例站点；接入实际设备前请修改站点信息'),
('GH-002', '二号温室', '示范园区 · B区', '环境监测示例站点；接入实际设备前请修改站点信息'),
('GH-003', '育苗温室', '示范园区 · C区', '环境监测示例站点；接入实际设备前请修改站点信息');
INSERT INTO station(code, name, location, description) VALUES
('ES-ALAVA', 'Álava 温室', '西班牙 · Álava', 'NUTRICONTROL / 马铃薯温室 / 原始记录时间，数据源未注明时区'),
('ES-NAVARRA', 'Navarra 温室', '西班牙 · Navarra · C0', 'NUTRICONTROL / 樱桃与观赏花卉温室 / 原始记录时间，数据源未注明时区'),
('ES-CARTAGENA', 'Cartagena 温室', '西班牙 · Cartagena · C0', 'NUTRICONTROL / 番茄温室 / 原始记录时间，数据源未注明时区');
