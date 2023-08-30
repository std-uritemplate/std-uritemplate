module StdUriTemplate

  # Public API
  public

  def self.expand(template, substitutions)
    expand_impl(template, substitutions)
  end

  # Private implementation
  private

  module Modifier
    NO_MOD = :no_mod
    PLUS = :plus
    DASH = :dash
    DOT = :dot
    SLASH = :slash
    SEMICOLON = :semicolon
    QUESTION_MARK = :question_mark
    AT = :at
  end

  def self.validate_literal(c, col)
    case c
    when '+', '#', '/', ';', '?', '&', ' ', '!', '=', '$', '|', '*', ':', '~', '-'
      raise ArgumentError, "Illegal character identified in the token at col:#{col}"
    end
  end

  def self.get_max_char(buffer, col)
    return -1 if buffer.nil?

    value = buffer.to_s

    if value.empty?
      -1
    else
      begin
        Integer(value)
      rescue ArgumentError
        raise ArgumentError, "Cannot parse max chars at col:#{col}"
      end
    end
  end

  def self.get_modifier(c, token, col)
    case c
    when '+'
      Modifier::PLUS
    when '#'
      Modifier::DASH
    when '.'
      Modifier::DOT
    when '/'
      Modifier::SLASH
    when ';'
      Modifier::SEMICOLON
    when '?'
      Modifier::QUESTION_MARK
    when '&'
      Modifier::AT
    else
      validate_literal(c, col)
      token << c
      Modifier::NO_MOD
    end
  end

  def self.expand_impl(str, substitutions)
    result = ''
    token = nil
    modifier = nil
    composite = false
    max_char_buffer = nil
    first_token = true

    str.chars.each_with_index do |character, i|
      case character
      when '{'
        token = ''
        first_token = true
      when '}'
        if token
          expanded = expand_token(modifier, token, composite, get_max_char(max_char_buffer, i), first_token, substitutions, result, i)
          first_token = false if expanded && first_token
          token = nil
          modifier = nil
          composite = false
          max_char_buffer = nil
        else
          raise ArgumentError, "Failed to expand token, invalid at col:#{i}"
        end
      when ','
        if token
          expanded = expand_token(modifier, token, composite, get_max_char(max_char_buffer, i), first_token, substitutions, result, i)
          first_token = false if expanded && first_token
          token = ''
          composite = false
          max_char_buffer = nil
        end
      else
        if token
          if modifier.nil?
            modifier = get_modifier(character, token, i)
          elsif max_char_buffer
            if character =~ /\d/
              max_char_buffer << character
            else
              raise ArgumentError, "Illegal character identified in the token at col:#{i}"
            end
          else
            if character == ':'
              max_char_buffer = ''
            elsif character == '*'
              composite = true
            else
              validate_literal(character, i)
              token << character
            end
          end
        else
          result << character
        end
      end
    end

    if token.nil?
      result
    else
      raise ArgumentError, 'Unterminated token'
    end
  end

  def self.add_prefix(mod, result)
    case mod
    when Modifier::DASH
      result << '#'
    when Modifier::DOT
      result << '.'
    when Modifier::SLASH
      result << '/'
    when Modifier::SEMICOLON
      result << ';'
    when Modifier::QUESTION_MARK
      result << '?'
    when Modifier::AT
      result << '&'
    end
  end

  def self.add_separator(mod, result)
    case mod
    when Modifier::DOT
      result << '.'
    when Modifier::SLASH
      result << '/'
    when Modifier::SEMICOLON
      result << ';'
    when Modifier::QUESTION_MARK, Modifier::AT
      result << '&'
    else
      result << ','
    end
  end

  def self.add_value(mod, token, value, result, max_char)
    case mod
    when Modifier::PLUS, Modifier::DASH
      add_expanded_value(value, result, max_char, false)
    when Modifier::QUESTION_MARK, Modifier::AT
      result << token + '='
      add_expanded_value(value, result, max_char, true)
    when Modifier::SEMICOLON
      result << token
      result << '=' unless value.empty?
      add_expanded_value(value, result, max_char, true)
    when Modifier::DOT, Modifier::SLASH, Modifier::NO_MOD
      add_expanded_value(value, result, max_char, true)
    end
  end

  def self.add_value_element(mod, token, value, result, max_char)
    case mod
    when Modifier::PLUS, Modifier::DASH
      add_expanded_value(value, result, max_char, false)
    when Modifier::QUESTION_MARK, Modifier::AT, Modifier::SEMICOLON, Modifier::DOT, Modifier::SLASH, Modifier::NO_MOD
      add_expanded_value(value, result, max_char, true)
    end
  end

  def self.add_expanded_value(value, result, max_char, replace_reserved)
    max = (max_char != -1) ? [max_char, value.length].min : value.length
    reserved_buffer = nil

    max.times do |i|
      character = value[i]

      if character == '%' && !replace_reserved
        reserved_buffer = ''
      end

      if reserved_buffer
        reserved_buffer << character

        if reserved_buffer.length == 3
          is_encoded = false
          begin
            encoded = URI.decode_www_form_component(reserved_buffer)
            is_encoded = !(encoded == reserved_buffer)
          rescue StandardError
          end

          if is_encoded
            result << reserved_buffer
          else
            result << "%25"
            result << reserved_buffer[1..-1] unless replace_reserved
          end

          reserved_buffer = nil
        end
      else
        if character == ' '
          result << "%20"
        elsif character == '%'
          result << "%25"
        else
          if replace_reserved
            result << URI.encode_www_form_component(character)
          else
            result << character
          end
        end
      end
    end

    if reserved_buffer
      result << "%25"
      if replace_reserved
        result << URI.encode_www_form_component(reserved_buffer[1..-1])
      else
        result << reserved_buffer[1..-1]
      end
    end
  end

  def self.list?(value)
    value.kind_of?(Array) || value.kind_of?(Enumerable)
  end

  def self.map?(value)
    value.kind_of?(Hash)
  end

  module SubstitutionType
    STRING = :string
    LIST = :list
    MAP = :map
  end

  def self.get_substitution_type(value, col)
    if (value.nil? || value.kind_of?(String))
      SubstitutionType::STRING
    elsif map?(value)
      SubstitutionType::MAP
    elsif list?(value)
      SubstitutionType::LIST
    else
      raise ArgumentError, "Illegal class passed as substitution, found #{value.class} at col:#{col}"
    end
  end

  def self.empty?(subst_type, value)
    case subst_type
    when SubstitutionType::STRING
      value.nil?
    when SubstitutionType::LIST
      value.respond_to?(:empty?) ? value.empty? : true
    when SubstitutionType::MAP
      value.respond_to?(:empty?) ? value.empty? : true
    else
      true
    end
  end

  def self.expand_token(modifier, token, composite, max_char, first_token, substitutions, result, col)
    raise ArgumentError, "Found an empty token at col:#{col}" if token.empty?

    value = substitutions[token]
    value = value.to_s if [Integer, Float].any? { |type| value.is_a?(type) }

    subst_type = get_substitution_type(value, col)
    return false if empty?(subst_type, value)

    if first_token
      add_prefix(modifier, result)
    else
      add_separator(modifier, result)
    end

    case subst_type
    when SubstitutionType::STRING
      add_string_value(modifier, token, value.to_s, result, max_char)
    when SubstitutionType::LIST
      add_list_value(modifier, token, value, result, max_char, composite)
    when SubstitutionType::MAP
      add_map_value(modifier, token, value, result, max_char, composite)
    end

    true
  end

  def self.add_string_value(modifier, token, value, result, max_char)
    add_value(modifier, token, value, result, max_char)
  end

  def self.add_list_value(modifier, token, value, result, max_char, composite)
    first = true
    value.each do |v|
      if first
        add_value(modifier, token, v.to_s, result, max_char)
        first = false
      else
        if composite
          add_separator(modifier, result)
          add_value(modifier, token, v.to_s, result, max_char)
        else
          result << ','
          add_value_element(modifier, token, v.to_s, result, max_char)
        end
      end
    end
    !first
  end

  def self.add_map_value(modifier, token, value, result, max_char, composite)
    first = true
    raise ArgumentError, 'Value trimming is not allowed on Maps' if max_char != -1

    value.each do |key, val|
      if composite
        add_separator(modifier, result) unless first
        add_value_element(modifier, token, key.to_s, result, max_char)
        result << '='
      else
        if first
          add_value(modifier, token, key.to_s, result, max_char)
        else
          result << ','
          add_value_element(modifier, token, key.to_s, result, max_char)
        end
        result << ','
      end
      add_value_element(modifier, token, val.to_s, result, max_char)
      first = false
    end
    !first
  end
end
